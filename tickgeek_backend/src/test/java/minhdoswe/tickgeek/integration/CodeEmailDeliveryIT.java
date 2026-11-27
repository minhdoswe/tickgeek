package minhdoswe.tickgeek.integration;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import minhdoswe.tickgeek.support.MutableClock;
import minhdoswe.tickgeek.support.RecordingEmailSender.SentEmail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** User Story 1: the code email is delivered (specs/002-sending-code/spec.md). */
class CodeEmailDeliveryIT extends AbstractEmailJobIT {

    /**
     * Proves a requested code is emailed once, right after the request, and becomes usable for
     * 10 minutes from when the provider accepted, while the job stops holding the code.
     * <p>
     * Cases: request for ana@example.com, sender accepts → 1 send to ana@example.com; outbox
     * SENT, attempts 1, published_at START, payload NULL; code ACTIVE, sent_at START, expires_at
     * START + 10 min (specs/002-sending-code/spec.md US1, FR-006, FR-008).
     */
    @Test
    void aRequestedCodeIsSentAndBecomesLive() throws Exception {
        // When: a code is requested and its first attempt runs
        requestCode(EMAIL).andExpect(status().isAccepted());
        UUID outboxId = outboxOf(EMAIL);
        awaitFirstAttempt(outboxId);

        // Then: one email went to ana@example.com
        assertThat(emailSender.calls()).extracting(SentEmail::to).containsExactly(EMAIL);
        // Then: the job is sent and holds no payload
        assertThat(outboxStatus(outboxId)).isEqualTo("SENT");
        assertThat(outboxTime("published_at", outboxId)).isEqualTo(MutableClock.START);
        assertThat(outboxText("payload", outboxId)).isNull();
        // Then: the code is live for 10 minutes from acceptance
        assertThat(codeStatus(outboxId)).isEqualTo("ACTIVE");
        assertThat(codeTime("sent_at", outboxId)).isEqualTo(MutableClock.START);
        assertThat(codeTime("expires_at", outboxId)).isEqualTo(MutableClock.START.plus(Duration.ofMinutes(10)));
    }

    /**
     * Proves a job that already finished is not sent again when it runs again, so a repeated
     * announcement never sends a second email.
     * <p>
     * Cases: sent job processed again → still 1 send, attempts 1
     * (specs/002-sending-code/spec.md FR-010).
     */
    @Test
    void aFinishedJobIsNotSentAgain() throws Exception {
        // Given: a code whose email was sent
        requestCode(EMAIL).andExpect(status().isAccepted());
        UUID outboxId = outboxOf(EMAIL);
        awaitFirstAttempt(outboxId);

        // When: the job runs again
        processor.process(outboxId);

        // Then: no second email and no second attempt
        assertThat(emailSender.calls()).hasSize(1);
        assertThat(attempts(outboxId)).isEqualTo(1);
    }
}
