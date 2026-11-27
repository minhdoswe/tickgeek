package minhdoswe.tickgeek.integration;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import minhdoswe.tickgeek.module.email.OutboxJobSteps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** User Story 4: stop sending a code that was replaced (specs/002-sending-code/spec.md). */
class CodeEmailReplacedIT extends AbstractEmailJobIT {

    private static final Duration PAST_COOLDOWN = Duration.ofSeconds(61);

    @Autowired
    private OutboxJobSteps steps;

    /**
     * Proves a replaced code's waiting email is never sent: its job is cancelled the next time it
     * runs and drops the old code, while the new code is sent.
     * <p>
     * Cases: code A's first attempt fails; after the cooldown code B is requested and sent;
     * A's retry runs → no send for A, outbox A CANCELLED with payload NULL, code B ACTIVE
     * (specs/002-sending-code/spec.md US4, FR-007b, SC-007; research.md R5).
     */
    @Test
    void aReplacedCodesRetryIsCancelled() throws Exception {
        // Given: code A's first attempt failed and its retry is waiting
        emailSender.failNext(1);
        requestCode(EMAIL).andExpect(status().isAccepted());
        UUID outboxA = outboxOf(EMAIL);
        awaitFirstAttempt(outboxA);

        // When: after the cooldown, code B is requested and sent
        clock.advance(PAST_COOLDOWN);
        requestCode(EMAIL).andExpect(status().isAccepted());
        UUID outboxB = outboxOf(EMAIL);
        awaitFirstAttempt(outboxB);

        // When: A's retry runs
        scheduler.runDue();

        // Then: only A's failed attempt and B were sent
        assertThat(emailSender.calls()).hasSize(2);
        // Then: A's job is cancelled and holds no code or address
        assertThat(outboxStatus(outboxA)).isEqualTo("CANCELLED");
        assertThat(outboxText("payload", outboxA)).isNull();
        assertThat(attempts(outboxA)).isEqualTo(1);
        // Then: B is the live code
        assertThat(outboxStatus(outboxB)).isEqualTo("SENT");
        assertThat(codeStatus(outboxB)).isEqualTo("ACTIVE");
    }

    /**
     * Proves a code replaced while its email was being sent is not brought back to life: the
     * late success leaves it dead, keeps the new code live, and still records the job as sent.
     * <p>
     * Cases: A's retry is claimed; code B is requested (A → INVALIDATED, B sent and ACTIVE); then
     * A's success is recorded → A stays INVALIDATED, B stays ACTIVE, outbox A SENT, no error
     * (specs/002-sending-code/research.md R3: the guard is in the UPDATE).
     */
    @Test
    void aCodeReplacedDuringItsSendStaysDead() throws Exception {
        // Given: code A's first attempt failed, and its retry is claimed after the cooldown
        emailSender.failNext(1);
        requestCode(EMAIL).andExpect(status().isAccepted());
        UUID outboxA = outboxOf(EMAIL);
        awaitFirstAttempt(outboxA);
        clock.advance(PAST_COOLDOWN);
        assertThat(steps.claim(outboxA, clock.instant())).isPresent();

        // When: code B is requested and sent while A's send is still running
        requestCode(EMAIL).andExpect(status().isAccepted());
        UUID outboxB = outboxOf(EMAIL);
        awaitFirstAttempt(outboxB);

        // When: A's send then succeeds
        steps.recordSuccess(outboxA, clock.instant());

        // Then: A stays dead, B stays live, and A's job is recorded as sent
        assertThat(codeStatus(outboxA)).isEqualTo("INVALIDATED");
        assertThat(codeStatus(outboxB)).isEqualTo("ACTIVE");
        assertThat(outboxStatus(outboxA)).isEqualTo("SENT");
    }
}
