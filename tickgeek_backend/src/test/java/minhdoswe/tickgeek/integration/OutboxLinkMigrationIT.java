package minhdoswe.tickgeek.integration;

import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import minhdoswe.tickgeek.support.TestPostgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs V3 and V4 on their own schema, starting from rows already in it, as production will. The
 * other integration tests start from an empty database, so they can't show what these
 * migrations do to existing rows or to the backend still running during a deploy.
 */
class OutboxLinkMigrationIT {

    private static final String SCHEMA = "outbox_link_migration";
    private static final UUID LINKED_OUTBOX = UUID.fromString("019a7a2c-0000-7000-8000-000000000001");
    private static final UUID UNLINKED_OUTBOX = UUID.fromString("019a7a2c-0000-7000-8000-000000000002");
    private static final UUID OLD_BACKEND_OUTBOX = UUID.fromString("019a7a2c-0000-7000-8000-000000000003");

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void startAtV2() {
        dataSource = new DriverManagerDataSource(TestPostgres.CONTAINER.getJdbcUrl(),
                TestPostgres.CONTAINER.getUsername(), TestPostgres.CONTAINER.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        migrateTo("2");
    }

    @AfterEach
    void dropSchema() {
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    /**
     * Proves V3 copies every existing link to the new column, so codes created before the deploy
     * still point to their email job and release 2 can make the column NOT NULL.
     * <p>
     * Cases: at V2, code 1 linked from an outbox and an outbox with no code → after V3, code 1
     * points to its outbox and the unlinked outbox is left alone
     * (specs/001-request-code/research.md R15).
     */
    @Test
    void copiesExistingLinksToTheCode() {
        // Given: at V2, a code with its outbox linked the old way, and an outbox with no code
        insertCodeTheOldWay();
        insertOutbox(LINKED_OUTBOX, 1L);
        insertOutbox(UNLINKED_OUTBOX, null);

        // When: V3 runs
        migrateTo("3");

        // Then: the code points to its outbox
        assertThat(jdbc.queryForObject("SELECT outbox_id FROM " + SCHEMA + ".verification_codes WHERE id = 1",
                UUID.class)).isEqualTo(LINKED_OUTBOX);
        // Then: the old link is still there for the backend that was running before the deploy
        assertThat(jdbc.queryForObject("SELECT verification_code_id FROM " + SCHEMA + ".outboxes WHERE id = ?",
                Long.class, LINKED_OUTBOX)).isEqualTo(1L);
    }

    /**
     * Proves the backend from before the deploy keeps working after V3, so code requests it
     * serves while the new backend starts don't fail.
     * <p>
     * Cases: after V3, the old writes (a code without outbox_id, then an outbox with
     * verification_code_id) → both succeed (specs/001-request-code/research.md R15).
     */
    @Test
    void oldBackendWritesStillWorkAfterV3() {
        // Given: V3 has run
        migrateTo("3");

        // When: the old backend saves a code and then its outbox, as it does today
        insertCodeTheOldWay();
        insertOutbox(OLD_BACKEND_OUTBOX, 1L);

        // Then: both rows were stored
        assertThat(jdbc.queryForObject("SELECT count(*) FROM " + SCHEMA + ".outboxes WHERE verification_code_id = 1",
                Integer.class)).isEqualTo(1);
    }

    /**
     * Proves V4 links codes that the pre-release-1 backend created while release 1 was
     * deploying, so making the column NOT NULL doesn't fail on them.
     * <p>
     * Cases: at V3, a code saved without outbox_id and its outbox linked the old way → after V4,
     * the code points to that outbox (specs/001-request-code/research.md R15).
     */
    @Test
    void v4LinksCodesCreatedDuringTheRelease1Deploy() {
        // Given: at V3, the old backend saved a code and its outbox the old way
        migrateTo("3");
        insertCodeTheOldWay();
        insertOutbox(OLD_BACKEND_OUTBOX, 1L);

        // When: V4 runs
        migrateTo("4");

        // Then: the code points to its outbox
        assertThat(jdbc.queryForObject("SELECT outbox_id FROM " + SCHEMA + ".verification_codes WHERE id = 1",
                UUID.class)).isEqualTo(OLD_BACKEND_OUTBOX);
    }

    /**
     * Proves that after V4 every code must have an outbox and the old link is gone, so a code
     * without its email job can't be stored and nothing can write the old column.
     * <p>
     * Cases: after V4, a code without outbox_id → refused; outboxes.verification_code_id and its
     * V2 index → no longer exist (specs/001-request-code/research.md R9, R15).
     */
    @Test
    void v4RequiresAnOutboxAndDropsTheOldLink() {
        // Given: V4 has run
        migrateTo("4");

        // When / Then: a code without an outbox is refused
        assertThatThrownBy(this::insertCodeTheOldWay).isInstanceOf(DataIntegrityViolationException.class);
        // Then: the old column and its index are gone
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns"
                + " WHERE table_schema = ? AND table_name = 'outboxes' AND column_name = 'verification_code_id'",
                Integer.class, SCHEMA)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes"
                + " WHERE schemaname = ? AND indexname = 'outboxes_verification_code_id_idx'",
                Integer.class, SCHEMA)).isZero();
    }

    /**
     * Proves the release 1 backend keeps working after V4, so code requests it serves while the
     * release 2 backend starts don't fail.
     * <p>
     * Cases: after V4, the release 1 writes (an outbox, then a code pointing to it) → both
     * succeed (specs/001-request-code/research.md R15).
     */
    @Test
    void release1BackendWritesStillWorkAfterV4() {
        // Given: V4 has run
        migrateTo("4");

        // When: the release 1 backend saves an outbox and then its code
        jdbc.update("INSERT INTO " + SCHEMA + ".outboxes (id, payload, status)"
                + " VALUES (?, '{}', 'PENDING'::" + SCHEMA + ".outbox_status)", LINKED_OUTBOX);
        jdbc.update("INSERT INTO " + SCHEMA + ".verification_codes"
                + " (email, code_hmac, status, request_source, outbox_id)"
                + " VALUES ('ana@x.com', 'hmac', 'PENDING', '203.0.113.7', ?)", LINKED_OUTBOX);

        // Then: the code points to its outbox
        assertThat(jdbc.queryForObject("SELECT outbox_id FROM " + SCHEMA + ".verification_codes", UUID.class))
                .isEqualTo(LINKED_OUTBOX);
    }

    private void migrateTo(String version) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .target(version)
                .load()
                .migrate();
    }

    private void insertCodeTheOldWay() {
        jdbc.update("INSERT INTO " + SCHEMA + ".verification_codes (id, email, code_hmac, status, request_source)"
                + " VALUES (1, 'ana@x.com', 'hmac', 'PENDING', '203.0.113.7')");
    }

    private void insertOutbox(UUID id, Long verificationCodeId) {
        jdbc.update("INSERT INTO " + SCHEMA + ".outboxes (id, payload, status, verification_code_id)"
                + " VALUES (?, '{}', 'PENDING'::" + SCHEMA + ".outbox_status, ?)", id, verificationCodeId);
    }
}
