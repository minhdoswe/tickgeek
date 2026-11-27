package minhdoswe.tickgeek.integration;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import minhdoswe.tickgeek.support.RecordingEmailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** User Story 3: give up and invalidate the code (specs/002-sending-code/spec.md). */
class CodeEmailGiveUpIT extends AbstractEmailJobIT {

    /**
     * Proves a code that can never arrive is killed after exactly 5 attempts, and the failed job
     * keeps only its last error, never the code or the address.
     * <p>
     * Cases: sender always fails → 5 sends; outbox ERROR, payload NULL, last error from the 5th
     * failure without the code; code INVALIDATED; running the job again sends nothing
     * (specs/002-sending-code/spec.md US3, FR-007, FR-008, SC-004).
     */
    @Test
    void aCodeIsInvalidatedAfterFiveFailedAttempts() throws Exception {
        // Given: the provider always fails
        emailSender.alwaysFail();

        // When: a code is requested and all 5 attempts run
        requestCode(EMAIL).andExpect(status().isAccepted());
        UUID outboxId = outboxOf(EMAIL);
        awaitFirstAttempt(outboxId);
        for (int retry = 0; retry < 4; retry++) {
            runNextAttempt(outboxId);
        }

        // Then: exactly 5 sends were made
        assertThat(emailSender.calls()).hasSize(5);
        // Then: the job failed with the last error and holds no code or address
        assertThat(outboxStatus(outboxId)).isEqualTo("ERROR");
        assertThat(outboxText("payload", outboxId)).isNull();
        assertThat(outboxText("last_error", outboxId))
                .isEqualTo("IllegalStateException: " + RecordingEmailSender.FAILURE_MESSAGE)
                .doesNotContain(EMAIL);
        // Then: the code is dead
        assertThat(codeStatus(outboxId)).isEqualTo("INVALIDATED");

        // When: the job runs again
        processor.process(outboxId);

        // Then: nothing more is sent
        assertThat(emailSender.calls()).hasSize(5);
    }
}
