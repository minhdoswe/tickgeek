package minhdoswe.tickgeek.integration;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.TaskScheduler;

import minhdoswe.tickgeek.module.email.EmailJobListener;
import minhdoswe.tickgeek.module.email.EmailJobProcessor;
import minhdoswe.tickgeek.module.email.emailsender.implement.BrevoEmailSender;
import minhdoswe.tickgeek.support.AbstractIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/** The app boots against PostgreSQL with every migration and validated JPA mappings. */
class ApplicationContextIT extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext context;

    /**
     * Proves the app starts on a real PostgreSQL with every migration applied and entities that
     * match the tables, so a schema mistake fails the build instead of production.
     * <p>
     * Cases: latest Flyway version → "5". Hibernate runs with ddl-auto=validate, so a mismatched
     * mapping fails startup before this test runs
     * (specs/001-request-code/research.md R14, R15).
     */
    @Test
    void schemaIsMigrated() {
        // When / Then: V5 is the latest applied migration
        assertThat(jdbc.queryForObject("SELECT max(version) FROM flyway_schema_history", String.class))
                .isEqualTo("5");
    }

    /**
     * Proves every foreign key column has an index, because Postgres doesn't create one. Without
     * it, each deleted parent row scans the whole child table: deleting 200 codes took 45 s
     * against 2M outboxes.
     * <p>
     * Cases: all foreign keys in the schema → none without an index on its column
     * (specs/001-request-code/research.md R15;
     * .claude/skills/supabase-postgres-best-practices/references/schema-foreign-key-indexes.md).
     */
    @Test
    void everyForeignKeyIsIndexed() {
        // When: looking for foreign key columns that no index covers
        List<String> unindexed = jdbc.queryForList("""
                SELECT c.conrelid::regclass || '.' || a.attname
                FROM pg_constraint c
                JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
                WHERE c.contype = 'f'
                  AND NOT EXISTS (
                      SELECT 1 FROM pg_index i
                      WHERE i.indrelid = c.conrelid AND a.attnum = ANY (i.indkey))
                """, String.class);

        // Then: there are none
        assertThat(unindexed).isEmpty();
    }

    /**
     * Proves the code email job is wired up, so a code request actually leads to an email.
     * <p>
     * Cases: the context holds a TaskScheduler, EmailJobListener and EmailJobProcessor
     * (specs/002-sending-code/research.md R1).
     */
    @Test
    void theEmailJobIsWired() {
        // When / Then: the scheduler and both job beans exist
        assertThat(context.getBeanNamesForType(TaskScheduler.class)).isNotEmpty();
        assertThat(context.getBean(EmailJobListener.class)).isNotNull();
        assertThat(context.getBean(EmailJobProcessor.class)).isNotNull();
    }

    /**
     * Proves that outside the local profile the app has a real email sender, so code emails do
     * not silently go nowhere.
     * <p>
     * Cases: default profile with a Brevo key and sender set → a BrevoEmailSender bean exists
     * (specs/002-sending-code/plan.md OQ2).
     */
    @Test
    void brevoSendsOutsideTheLocalProfile() {
        // When / Then: the Brevo sender is in the context
        assertThat(context.getBean(BrevoEmailSender.class)).isNotNull();
    }
}
