package minhdoswe.tickgeek.integration;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;

import minhdoswe.tickgeek.module.email.EmailJobProcessor;
import minhdoswe.tickgeek.support.AbstractIntegrationTest;

import static org.awaitility.Awaitility.await;

/**
 * Helpers for the code email job tests. The first attempt runs by itself after the request
 * commits. A retry is held by {@link minhdoswe.tickgeek.support.ControllableTaskScheduler} until
 * the test moves the clock to it and runs it (specs/002-sending-code/tasks.md "How the
 * integration tests drive the job").
 */
abstract class AbstractEmailJobIT extends AbstractIntegrationTest {

    protected static final String EMAIL = "ana@example.com";
    private static final Duration FIRST_ATTEMPT_TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    protected EmailJobProcessor processor;

    protected UUID outboxOf(String email) {
        return jdbc.queryForObject("SELECT outbox_id FROM verification_codes WHERE email = ? ORDER BY id DESC LIMIT 1",
                UUID.class, email);
    }

    protected String outboxStatus(UUID outboxId) {
        return jdbc.queryForObject("SELECT status::text FROM outboxes WHERE id = ?", String.class, outboxId);
    }

    protected String codeStatus(UUID outboxId) {
        return jdbc.queryForObject("SELECT status FROM verification_codes WHERE outbox_id = ?", String.class, outboxId);
    }

    protected int attempts(UUID outboxId) {
        return count("SELECT attempts FROM outboxes WHERE id = ?", outboxId);
    }

    protected Instant outboxTime(String column, UUID outboxId) {
        return instant("SELECT " + column + " FROM outboxes WHERE id = ?", outboxId);
    }

    protected Instant codeTime(String column, UUID outboxId) {
        return instant("SELECT " + column + " FROM verification_codes WHERE outbox_id = ?", outboxId);
    }

    protected String outboxText(String column, UUID outboxId) {
        return jdbc.queryForObject("SELECT " + column + " FROM outboxes WHERE id = ?", String.class, outboxId);
    }

    /** Waits until the attempt started by the code request has finished (attempts is 1). */
    protected void awaitFirstAttempt(UUID outboxId) {
        await().atMost(FIRST_ATTEMPT_TIMEOUT).until(() -> attempts(outboxId) == 1
                && (!"PENDING".equals(outboxStatus(outboxId)) || outboxText("last_error", outboxId) != null));
    }

    /** Moves the clock to the job's next attempt time and runs the retry scheduled for it. */
    protected void runNextAttempt(UUID outboxId) {
        clock.advance(Duration.between(clock.instant(), outboxTime("next_attempt_at", outboxId)));
        scheduler.runDue();
    }

    private Instant instant(String sql, UUID outboxId) {
        Timestamp value = jdbc.queryForObject(sql, Timestamp.class, outboxId);
        return value == null ? null : value.toInstant();
    }
}
