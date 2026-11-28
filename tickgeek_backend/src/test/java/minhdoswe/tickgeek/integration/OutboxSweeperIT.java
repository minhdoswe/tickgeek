package minhdoswe.tickgeek.integration;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import minhdoswe.tickgeek.module.email.OutboxJobSteps;
import minhdoswe.tickgeek.module.email.OutboxSweeper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The sweeper recovers jobs whose in-memory event was lost, and the locked, leased claim keeps
 * any job from being sent twice (specs/002-sending-code/research.md R1).
 */
class OutboxSweeperIT extends AbstractEmailJobIT {

    private static final Duration LEASE = Duration.ofMinutes(1);

    @Autowired
    private OutboxSweeper sweeper;

    @Autowired
    private OutboxJobSteps steps;

    @Autowired
    private TransactionTemplate transactions;

    /**
     * Proves a retry lost in a restart is still sent: the sweeper finds the due PENDING job.
     * <p>
     * Cases: attempt 1 fails, held retry dropped, clock at next attempt → sweep sends it,
     * SENT, code ACTIVE, 2 sends.
     */
    @Test
    void aLostRetryIsSentByTheSweeper() throws Exception {
        // Given: the first attempt failed and the in-memory retry was lost
        UUID outboxId = failedFirstAttempt();
        scheduler.clear();

        // When: the retry is due and a sweep runs
        moveClockToNextAttempt(outboxId);
        sweeper.sweep();

        // Then: the job is sent and the code is live
        assertThat(emailSender.calls()).hasSize(2);
        assertThat(outboxStatus(outboxId)).isEqualTo("SENT");
        assertThat(codeStatus(outboxId)).isEqualTo("ACTIVE");
    }

    /**
     * Proves a send that crashed after its claim is left alone while its lease lasts, then sent
     * again once the lease runs out.
     * <p>
     * Cases: attempt 2 claimed at T and never recorded; sweep at T + 59 s → no send, attempts 2;
     * sweep at T + 1 min → sent, attempts 3.
     */
    @Test
    void aCrashedSendIsRetriedOnlyAfterItsLease() throws Exception {
        // Given: attempt 2 was claimed and the process died before sending or recording it
        UUID outboxId = failedFirstAttempt();
        scheduler.clear();
        moveClockToNextAttempt(outboxId);
        assertThat(steps.claim(outboxId, clock.instant())).isPresent();
        int callsBefore = emailSender.calls().size();

        // When: a sweep runs while the lease still holds
        clock.advance(LEASE.minusSeconds(1));
        sweeper.sweep();

        // Then: nothing is sent
        assertThat(emailSender.calls()).hasSize(callsBefore);
        assertThat(attempts(outboxId)).isEqualTo(2);

        // When: the lease runs out and a sweep runs
        clock.advance(Duration.ofSeconds(1));
        sweeper.sweep();

        // Then: it is sent as attempt 3
        assertThat(emailSender.calls()).hasSize(callsBefore + 1);
        assertThat(outboxStatus(outboxId)).isEqualTo("SENT");
        assertThat(attempts(outboxId)).isEqualTo(3);
    }

    /**
     * Proves a job another transaction is claiming is skipped at once instead of waited for.
     * <p>
     * Cases: row locked by another transaction → process returns in time, no send, attempts 1.
     */
    @Test
    void aJobAnotherRunHoldsIsSkipped() throws Exception {
        // Given: a due job whose row another transaction has locked
        UUID outboxId = failedFirstAttempt();
        scheduler.clear();
        moveClockToNextAttempt(outboxId);

        transactions.executeWithoutResult(tx -> {
            jdbc.queryForObject("SELECT id FROM outboxes WHERE id = ? FOR UPDATE", UUID.class, outboxId);

            // When: the job is processed from another thread meanwhile
            CompletableFuture.runAsync(() -> processor.process(outboxId)).orTimeout(5, TimeUnit.SECONDS).join();
        });

        // Then: it was skipped, not sent
        assertThat(emailSender.calls()).hasSize(1);
        assertThat(attempts(outboxId)).isEqualTo(1);
    }

    /**
     * Proves the event path and the sweeper running the same due job at the same moment send it
     * only once.
     * <p>
     * Cases: two runs of a due job started together → 1 new send, attempts 2, SENT.
     */
    @Test
    void twoRunsAtOnceSendOnce() throws Exception {
        // Given: a due job
        UUID outboxId = failedFirstAttempt();
        scheduler.clear();
        moveClockToNextAttempt(outboxId);

        // When: two runs start together
        CyclicBarrier start = new CyclicBarrier(2);
        List<CompletableFuture<Void>> runs = List.of(runAfter(start, outboxId), runAfter(start, outboxId));
        CompletableFuture.allOf(runs.toArray(CompletableFuture[]::new)).orTimeout(10, TimeUnit.SECONDS).join();

        // Then: only one of them sent it
        assertThat(emailSender.calls()).hasSize(2);
        assertThat(attempts(outboxId)).isEqualTo(2);
        assertThat(outboxStatus(outboxId)).isEqualTo("SENT");
    }

    /** Requests a code whose first attempt fails, and waits for that attempt to finish. */
    private UUID failedFirstAttempt() throws Exception {
        emailSender.failNext(1);
        requestCode(EMAIL).andExpect(status().isAccepted());
        UUID outboxId = outboxOf(EMAIL);
        awaitFirstAttempt(outboxId);
        return outboxId;
    }

    private void moveClockToNextAttempt(UUID outboxId) {
        clock.advance(Duration.between(clock.instant(), outboxTime("next_attempt_at", outboxId)));
    }

    private CompletableFuture<Void> runAfter(CyclicBarrier start, UUID outboxId) {
        return CompletableFuture.runAsync(() -> {
            try {
                start.await(5, TimeUnit.SECONDS);
            } catch (Exception interrupted) {
                throw new IllegalStateException(interrupted);
            }
            processor.process(outboxId);
        });
    }
}
