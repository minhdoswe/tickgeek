package minhdoswe.tickgeek.module.email;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import minhdoswe.tickgeek.support.TestProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxJobStepsTest {

    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");
    private static final UUID ID = UUID.fromString("019a7a2c-0000-7000-8000-000000000009");
    private static final String PAYLOAD = "{payload}";
    private static final Duration LEASE = TestProperties.email().claimLease();

    private final OutboxRepository outboxes = mock(OutboxRepository.class);
    private final OutboxJobOwner owner = mock(OutboxJobOwner.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final OutboxJobSteps steps = new OutboxJobSteps(outboxes, owner, TestProperties.email(), events);

    /**
     * Proves a due, wanted job is claimed and handed its payload, so the send can start with the
     * attempt already counted.
     * <p>
     * Cases: PENDING, due now, attempts 0, code still wanted → payload returned, attempts 1
     * (specs/002-sending-code/research.md R2, R3).
     */
    @Test
    void claimsADueWantedJob() {
        // Given: a pending job due now whose code still wants it
        Outbox job = stored(Outbox.pending(PAYLOAD, NOW));
        when(owner.isStillWanted(ID)).thenReturn(true);

        // When: an attempt is claimed at NOW
        Optional<String> claimed = steps.claim(ID, NOW);

        // Then: the payload comes back, the attempt is counted and the job is leased
        assertThat(claimed).contains(PAYLOAD);
        assertThat(job.getAttempts()).isEqualTo(1);
        assertThat(job.getNextAttemptAt()).isEqualTo(NOW.plus(LEASE));
    }

    /**
     * Proves a job claimed moments ago is not claimed again while its send may still be running,
     * so a second event or a sweep doesn't send it twice.
     * <p>
     * Cases: claimed at NOW, claimed again at NOW + 59 s → empty, attempts stay 1
     * (specs/002-sending-code/research.md R1, R3).
     */
    @Test
    void doesNotClaimAJobInsideItsLease() {
        // Given: a job claimed at NOW whose code still wants it
        Outbox job = stored(Outbox.pending(PAYLOAD, NOW));
        when(owner.isStillWanted(ID)).thenReturn(true);
        steps.claim(ID, NOW);

        // When / Then: claiming again before the lease ends gives nothing
        assertThat(steps.claim(ID, NOW.plus(LEASE).minusSeconds(1))).isEmpty();
        assertThat(job.getAttempts()).isEqualTo(1);
    }

    /**
     * Proves a job another run is claiming right now is skipped, not waited for.
     * <p>
     * Cases: row locked elsewhere (lockById empty) → empty, owner not asked
     * (specs/002-sending-code/research.md R1).
     */
    @Test
    void skipsAJobAnotherRunHolds() {
        // Given: the row is locked by another transaction
        when(outboxes.lockById(ID)).thenReturn(Optional.empty());

        // When / Then: nothing is claimed
        assertThat(steps.claim(ID, NOW)).isEmpty();
        verify(owner, never()).isStillWanted(any());
    }

    /**
     * Proves a finished job is never sent again, even when it is announced again.
     * <p>
     * Cases: SENT job → empty, not claimed, owner not asked (specs/002-sending-code/spec.md FR-010).
     */
    @Test
    void doesNotClaimAFinishedJob() {
        // Given: a job that was already sent
        Outbox job = Outbox.pending(PAYLOAD, NOW);
        job.succeed(NOW);
        stored(job);

        // When / Then: nothing is claimed
        assertThat(steps.claim(ID, NOW)).isEmpty();
        assertThat(job.getAttempts()).isZero();
        verify(owner, never()).isStillWanted(any());
    }

    /**
     * Proves a job is not tried before its retry time, so the waits between attempts hold.
     * <p>
     * Cases: due at NOW + 5 s, claimed at NOW → empty, not claimed
     * (specs/002-sending-code/research.md R3).
     */
    @Test
    void doesNotClaimAJobBeforeItIsDue() {
        // Given: a job due in 5 seconds whose code still wants it
        Outbox job = stored(Outbox.pending(PAYLOAD, NOW.plusSeconds(5)));
        when(owner.isStillWanted(ID)).thenReturn(true);

        // When / Then: claiming now gives nothing
        assertThat(steps.claim(ID, NOW)).isEmpty();
        assertThat(job.getAttempts()).isZero();
    }

    /**
     * Proves a success marks the job sent and makes its code live in one step, so a sent email
     * never leaves its code unusable.
     * <p>
     * Cases: success at NOW → SENT and owner.delivered(id, NOW)
     * (specs/002-sending-code/data-model.md "deliver").
     */
    @Test
    void successMarksSentAndDeliversToTheOwner() {
        // Given: a claimed job
        Outbox job = stored(Outbox.pending(PAYLOAD, NOW));
        job.claim(NOW, LEASE);

        // When: the success is recorded
        steps.recordSuccess(ID, NOW);

        // Then: the job is sent and the owner hears about it
        assertThat(job.getStatus()).isEqualTo(OutboxStatus.SENT);
        verify(owner).delivered(ID, NOW);
    }

    /**
     * Proves a failed attempt with retries left is rescheduled and announced again, so the job
     * runs once more after the wait.
     * <p>
     * Cases: attempt 1 failed at NOW → next attempt at NOW + 5 s, last error kept, event with a
     * 5 s delay, owner not told (specs/002-sending-code/research.md R1, R6).
     */
    @Test
    void aFailureWithRetriesLeftIsRescheduled() {
        // Given: a job whose first attempt was claimed
        Outbox job = stored(Outbox.pending(PAYLOAD, NOW));
        job.claim(NOW, LEASE);

        // When: the attempt failed
        steps.recordFailure(ID, NOW, "IllegalStateException: provider unavailable");

        // Then: it is rescheduled in 5 seconds and announced again
        assertThat(job.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(job.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(job.getLastError()).isEqualTo("IllegalStateException: provider unavailable");
        verify(events).publishEvent(new OutboxJobScheduled(ID, Duration.ofSeconds(5)));
        verify(owner, never()).abandoned(any());
    }

    /**
     * Proves the last allowed failure gives up for good: the job fails with that error, its code
     * is invalidated, and no further attempt is scheduled.
     * <p>
     * Cases: attempt 5 of 5 failed → ERROR with the error, owner.abandoned, no event
     * (specs/002-sending-code/spec.md FR-007; research.md R6).
     */
    @Test
    void theLastAllowedFailureGivesUp() {
        // Given: a job whose fifth attempt was claimed
        Outbox job = stored(Outbox.pending(PAYLOAD, NOW));
        for (int attempt = 0; attempt < 5; attempt++) {
            job.claim(NOW, LEASE);
        }

        // When: that attempt failed
        steps.recordFailure(ID, NOW, "IllegalStateException: provider unavailable");

        // Then: the job failed for good and its code is invalidated
        assertThat(job.getStatus()).isEqualTo(OutboxStatus.ERROR);
        assertThat(job.getLastError()).isEqualTo("IllegalStateException: provider unavailable");
        verify(owner).abandoned(ID);
        verify(events, never()).publishEvent(any(Object.class));
    }

    /**
     * Proves a job that already used every attempt is not tried again, even if an earlier
     * attempt crashed before recording its result: it gives up instead.
     * <p>
     * Cases: PENDING, attempts 5 of 5, lease over → empty, ERROR "max attempts reached",
     * owner.abandoned (specs/002-sending-code/research.md R3).
     */
    @Test
    void aJobWithNoAttemptsLeftGivesUpInsteadOfSending() {
        // Given: a pending job that used its 5 attempts, the last one crashed, and its lease ran out
        Outbox job = stored(Outbox.pending(PAYLOAD, NOW));
        for (int attempt = 0; attempt < 5; attempt++) {
            job.claim(NOW, LEASE);
        }
        when(owner.isStillWanted(ID)).thenReturn(true);

        // When / Then: nothing is claimed and the job gives up
        assertThat(steps.claim(ID, NOW.plus(LEASE))).isEmpty();
        assertThat(job.getStatus()).isEqualTo(OutboxStatus.ERROR);
        assertThat(job.getLastError()).isEqualTo(OutboxJobSteps.MAX_ATTEMPTS_REACHED);
        assertThat(job.getAttempts()).isEqualTo(5);
        verify(owner).abandoned(ID);
    }

    /**
     * Proves a job whose code was replaced is cancelled before any attempt, so the user never gets
     * a code that no longer works.
     * <p>
     * Cases: PENDING, due, code no longer wanted → empty, CANCELLED, attempts unchanged
     * (specs/002-sending-code/spec.md US4, FR-007b; research.md R3, R5).
     */
    @Test
    void aJobWhoseCodeWasReplacedIsCancelledNotClaimed() {
        // Given: a pending, due job whose code was replaced
        Outbox job = stored(Outbox.pending(PAYLOAD, NOW));
        when(owner.isStillWanted(ID)).thenReturn(false);

        // When / Then: nothing is claimed and the job is cancelled
        assertThat(steps.claim(ID, NOW)).isEmpty();
        assertThat(job.getStatus()).isEqualTo(OutboxStatus.CANCELLED);
        assertThat(job.getAttempts()).isZero();
    }

    private Outbox stored(Outbox job) {
        ReflectionTestUtils.setField(job, "id", ID);
        when(outboxes.lockById(ID)).thenReturn(Optional.of(job));
        when(outboxes.findById(ID)).thenReturn(Optional.of(job));
        return job;
    }
}
