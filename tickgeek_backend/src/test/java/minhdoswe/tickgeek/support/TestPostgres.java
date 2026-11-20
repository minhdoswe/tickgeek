package minhdoswe.tickgeek.support;

import org.testcontainers.postgresql.PostgreSQLContainer;

/** One PostgreSQL container shared by every test class in the run. */
public final class TestPostgres {

    public static final PostgreSQLContainer CONTAINER = new PostgreSQLContainer("postgres:17-alpine");

    static {
        CONTAINER.start();
    }

    private TestPostgres() {
    }
}
