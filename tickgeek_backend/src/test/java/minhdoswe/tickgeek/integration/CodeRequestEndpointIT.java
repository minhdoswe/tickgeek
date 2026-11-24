package minhdoswe.tickgeek.integration;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import minhdoswe.tickgeek.support.AbstractIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CodeRequestEndpointIT extends AbstractIntegrationTest {

    /**
     * Proves a valid code request over HTTP is accepted and stores everything sign-in needs, so
     * the user gets a code email and the next screen can show the right details.
     * <p>
     * Cases: " Ana@Example.com " → 202 with email "ana@example.com", 10 minutes and resend at
     * 10:01:00; one live code, one outbox job and the caller's IP are stored
     * (specs/001-request-code/contracts/auth-api.md POST /api/auth/code-requests;
     * specs/001-request-code/spec.md FR-002, FR-003).
     */
    @Test
    void acceptsAndNormalizesTheEmail() throws Exception {
        // When / Then: 202 with the contract body (the test clock starts at 10:00:00)
        requestCode(" Ana@Example.com ")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.email").value("ana@example.com"))
                .andExpect(jsonPath("$.codeValidMinutes").value(10))
                .andExpect(jsonPath("$.resendAvailableAt").value("2026-11-11T10:01:00Z"));

        // Then: one live code, one queued email and the request source were stored
        assertThat(count("SELECT count(*) FROM verification_codes WHERE email = 'ana@example.com' AND status IN ('PENDING', 'ACTIVE')"))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM outboxes WHERE verification_code_id IS NOT NULL")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT request_source FROM verification_codes", String.class))
                .isEqualTo(DEFAULT_SOURCE);
    }

    /**
     * Proves bad input is refused with a clear 400 instead of a server error, so the sign-in form
     * can show the user what to fix.
     * <p>
     * Cases: "not-an-email" → 400 VALIDATION_FAILED with an error under "email"; a body that
     * isn't JSON → 400 VALIDATION_FAILED (specs/001-request-code/contracts/auth-api.md 400;
     * specs/001-request-code/spec.md FR-002).
     */
    @Test
    void malformedEmailIsRejected() throws Exception {
        // When / Then: a malformed email gets a field error
        requestCode("not-an-email")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.email").isArray());
        // When / Then: a body that is not valid JSON is rejected too
        postJson("/api/auth/code-requests", "{not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    /**
     * Proves an early resend over HTTP gets the wait in both the Retry-After header and the
     * message, so the frontend can show a countdown and the user knows when to retry.
     * <p>
     * Cases: second request 15 s after the first → 429 TOO_MANY_REQUESTS, Retry-After: 45 and
     * "45 seconds" in the message. Only the cooldown may reveal the wait
     * (specs/001-request-code/spec.md FR-008; specs/001-request-code/contracts/auth-api.md 429).
     */
    @Test
    void resendWithinTheCooldownIsRefusedWithTheWait() throws Exception {
        // Given: a first request, then 15 s pass
        requestCode("ana@x.com");
        clock.advance(Duration.ofSeconds(15));

        // When / Then: 429 with 45 s to wait in both the header and the message
        requestCode("ana@x.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "45"))
                .andExpect(jsonPath("$.error").value("TOO_MANY_REQUESTS"))
                .andExpect(jsonPath("$.message").value(containsString("45 seconds")));
    }

    /**
     * Proves the per-email limit is refused over HTTP with a generic message and no Retry-After,
     * so the response doesn't reveal when the limit resets.
     * <p>
     * Cases: 5 requests 60 s apart → accepted; 6th → 429 with "Too many requests. Please try
     * again later." and no Retry-After (specs/001-request-code/spec.md FR-008, FR-020;
     * specs/001-request-code/contracts/auth-api.md 429).
     */
    @Test
    void sixthRequestInAnHourIsRefusedGenerically() throws Exception {
        // Given: 5 accepted requests, 60 s apart so the cooldown never blocks them
        for (int i = 0; i < 5; i++) {
            requestCode("ana@x.com").andExpect(status().isAccepted());
            clock.advance(Duration.ofSeconds(60));
        }

        // When / Then: the 6th gets 429 with the generic message and no Retry-After
        requestCode("ana@x.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().doesNotExist("Retry-After"))
                .andExpect(jsonPath("$.message").value("Too many requests. Please try again later."));
    }

    /**
     * Proves outbox jobs get time-ordered ids, so new rows go to the end of the primary key
     * index instead of random spots, and the index stays compact as the table grows.
     * <p>
     * Cases: one accepted request → its outbox id is a version 7 UUID
     * (specs/001-request-code/research.md R9).
     */
    @Test
    void outboxJobsGetTimeOrderedIds() throws Exception {
        // When: a code is requested
        requestCode("ana@x.com").andExpect(status().isAccepted());

        // Then: the stored outbox id is a UUIDv7
        UUID id = jdbc.queryForObject("SELECT id FROM outboxes", UUID.class);
        assertThat(id.version()).isEqualTo(7);
    }
}
