package minhdoswe.tickgeek.integration;

import java.util.List;

import org.junit.jupiter.api.Test;

import minhdoswe.tickgeek.support.AbstractIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/** The app boots against PostgreSQL with every migration and validated JPA mappings. */
class ApplicationContextIT extends AbstractIntegrationTest {

    /**
     * Proves the app starts on a real PostgreSQL with every migration applied and entities that
     * match the tables, so a schema mistake fails the build instead of production.
     * <p>
     * Cases: latest Flyway version → "4". Hibernate runs with ddl-auto=validate, so a mismatched
     * mapping fails startup before this test runs
     * (specs/001-request-code/research.md R14, R15).
     */
    @Test
    void schemaIsMigrated() {
        // When / Then: V4 is the latest applied migration
        assertThat(jdbc.queryForObject("SELECT max(version) FROM flyway_schema_history", String.class))
                .isEqualTo("4");
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
}
