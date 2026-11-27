package minhdoswe.tickgeek.integration;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import minhdoswe.tickgeek.support.MutableClock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** User Story 2: retry with growing waits (specs/002-sending-code/spec.md). */
class CodeEmailRetryIT extends AbstractEmailJobIT {

    /**
     * Proves a code still arrives when the provider fails twice: the job waits 5 s, then 10 s,
     * resends in full each time, and the code's 10 minutes start at the attempt that worked.
     * <p>
     * Cases: sender fails 2 calls → after attempt 1 next at START + 5 s, after attempt 2 next
     * at START + 15 s, attempt 3 at START + 15 s succeeds → 3 sends, SENT, code ACTIVE until
     * START + 15 s + 10 min (specs/002-sending-code/spec.md US2, FR-004, FR-005).
     */
    @Test
    void aCodeArrivesAfterTwoFailedAttempts() throws Exception {
        // Given: the provider fails the next 2 sends
        emailSender.failNext(2);

        // When: a code is requested and its first attempt fails
        requestCode(EMAIL).andExpect(status().isAccepted());
        UUID outboxId = outboxOf(EMAIL);
        awaitFirstAttempt(outboxId);

        // Then: the job waits 5 seconds and keeps the error
        assertThat(outboxStatus(outboxId)).isEqualTo("PENDING");
        assertThat(outboxTime("next_attempt_at", outboxId)).isEqualTo(MutableClock.START.plusSeconds(5));
        assertThat(outboxText("last_error", outboxId)).isEqualTo("IllegalStateException: provider unavailable");

        // When: the second attempt runs and fails
        runNextAttempt(outboxId);

        // Then: the next wait is 10 seconds
        assertThat(outboxTime("next_attempt_at", outboxId)).isEqualTo(MutableClock.START.plusSeconds(15));

        // When: the third attempt runs and succeeds
        runNextAttempt(outboxId);

        // Then: 3 sends, the job is sent, and the code lives 10 minutes from the third attempt
        Instant acceptedAt = MutableClock.START.plusSeconds(15);
        assertThat(emailSender.calls()).hasSize(3);
        assertThat(outboxStatus(outboxId)).isEqualTo("SENT");
        assertThat(attempts(outboxId)).isEqualTo(3);
        assertThat(codeStatus(outboxId)).isEqualTo("ACTIVE");
        assertThat(codeTime("expires_at", outboxId)).isEqualTo(acceptedAt.plus(Duration.ofMinutes(10)));
    }
}
