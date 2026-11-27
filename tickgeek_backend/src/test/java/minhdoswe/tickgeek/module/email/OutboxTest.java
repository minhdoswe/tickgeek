package minhdoswe.tickgeek.module.email;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxTest {

    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(3);

    /**
     * Proves claiming counts the attempt, so a job that keeps failing or crashing still reaches
     * the attempt limit.
     * <p>
     * Cases: new job → attempts 0; claim twice → 2 (specs/002-sending-code/research.md R2).
     */
    @Test
    void claimCountsTheAttempt() {
        // Given: a new job
        Outbox job = Outbox.pending("{payload}", NOW);

        // When: two attempts are claimed
        job.claim();
        job.claim();

        // Then: both are counted
        assertThat(job.getAttempts()).isEqualTo(2);
    }

    /**
     * Proves a sent job is recorded as sent and no longer holds the code or the address, so a
     * database leak of finished jobs reveals nothing.
     * <p>
     * Cases: PENDING job succeeds at LATER → SENT, publishedAt LATER, payload null
     * (specs/002-sending-code/spec.md FR-006, FR-008).
     */
    @Test
    void successMarksTheJobSentAndErasesThePayload() {
        // Given: a pending job
        Outbox job = Outbox.pending("{payload}", NOW);

        // When: a provider accepted it at LATER
        job.succeed(LATER);

        // Then: it is sent at LATER and the payload is gone
        assertThat(job.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(job.getPublishedAt()).isEqualTo(LATER);
        assertThat(job.getPayload()).isNull();
    }

    /**
     * Proves a finished job can't be changed again, so a late or repeated result never rewrites
     * what already happened.
     * <p>
     * Cases: SENT job, then claim and succeed again → attempts and publishedAt unchanged
     * (specs/002-sending-code/data-model.md "only while PENDING").
     */
    @Test
    void aFinishedJobIgnoresFurtherChanges() {
        // Given: a job that was already sent
        Outbox job = Outbox.pending("{payload}", NOW);
        job.succeed(NOW);

        // When: it is claimed and succeeds again
        job.claim();
        job.succeed(LATER);

        // Then: nothing changed
        assertThat(job.getAttempts()).isZero();
        assertThat(job.getPublishedAt()).isEqualTo(NOW);
    }

    /**
     * Proves each retry waits twice as long as the one before, so a provider outage gets 5, 10,
     * 20 and 40 seconds to recover.
     * <p>
     * Cases: failure after attempts 1, 2, 3, 4 with base 5 s → next attempt at +5, +10, +20, +40 s,
     * last error kept (specs/002-sending-code/spec.md FR-004; research.md R6).
     */
    @Test
    void retryWaitsDoubleAfterEachFailure() {
        // Given: a pending job
        Outbox job = Outbox.pending("{payload}", NOW);

        for (long expectedWait : new long[] {5, 10, 20, 40}) {
            // When: an attempt is claimed and fails at NOW
            job.claim();
            job.retryLater(NOW, "boom", Duration.ofSeconds(5));

            // Then: the next attempt waits twice as long as the last one
            assertThat(job.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(expectedWait));
            assertThat(job.getLastError()).isEqualTo("boom");
        }
    }

    /**
     * Proves a finished job is not rescheduled by a late failure.
     * <p>
     * Cases: SENT job, retryLater → next attempt and last error unchanged
     * (specs/002-sending-code/data-model.md "only while PENDING").
     */
    @Test
    void aFinishedJobIsNotRescheduled() {
        // Given: a job that was already sent
        Outbox job = Outbox.pending("{payload}", NOW);
        job.claim();
        job.succeed(NOW);

        // When: a failure arrives
        job.retryLater(LATER, "boom", Duration.ofSeconds(5));

        // Then: nothing changed
        assertThat(job.getNextAttemptAt()).isEqualTo(NOW);
        assertThat(job.getLastError()).isNull();
    }

    /**
     * Proves a job that gives up is recorded as failed with its reason and no longer holds the
     * code or the address.
     * <p>
     * Cases: PENDING job gives up with "boom" → ERROR, last error "boom", payload null; a second
     * give-up on the finished job changes nothing (specs/002-sending-code/spec.md FR-007, FR-008).
     */
    @Test
    void givingUpRecordsTheFailureAndErasesThePayload() {
        // Given: a pending job
        Outbox job = Outbox.pending("{payload}", NOW);

        // When: it gives up, twice
        job.giveUp("boom");
        job.giveUp("later");

        // Then: it failed with the first reason and holds no payload
        assertThat(job.getStatus()).isEqualTo(OutboxStatus.ERROR);
        assertThat(job.getLastError()).isEqualTo("boom");
        assertThat(job.getPayload()).isNull();
    }

    /**
     * Proves a cancelled job no longer holds the replaced code or the address, and a finished job
     * can't be cancelled.
     * <p>
     * Cases: PENDING job cancelled → CANCELLED, payload null; SENT job cancelled → stays SENT
     * (specs/002-sending-code/spec.md US4; research.md R5).
     */
    @Test
    void cancellingErasesThePayloadOnlyWhilePending() {
        // Given: a pending job and a sent job
        Outbox pending = Outbox.pending("{payload}", NOW);
        Outbox sent = Outbox.pending("{payload}", NOW);
        sent.succeed(NOW);

        // When: both are cancelled
        pending.cancel();
        sent.cancel();

        // Then: only the pending one is cancelled, and it holds no payload
        assertThat(pending.getStatus()).isEqualTo(OutboxStatus.CANCELLED);
        assertThat(pending.getPayload()).isNull();
        assertThat(sent.getStatus()).isEqualTo(OutboxStatus.SENT);
    }
}
