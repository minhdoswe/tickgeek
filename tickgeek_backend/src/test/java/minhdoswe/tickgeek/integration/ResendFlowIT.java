package minhdoswe.tickgeek.integration;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import minhdoswe.tickgeek.support.AbstractIntegrationTest;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Resend limits (specs/001-request-code/spec.md User Story 2). */
class ResendFlowIT extends AbstractIntegrationTest {

    /**
     * Proves a user who presses resend too early is told exactly when they can try again, so they
     * wait instead of retrying blindly.
     * <p>
     * Cases: resend 20 s after the first request → 429, Retry-After: 40 and "40 seconds" in the
     * message (specs/001-request-code/spec.md User Story 2, FR-008).
     */
    @Test
    void resendWithinSixtySecondsStatesTheWait() throws Exception {
        // Given: a first request, then 20 s pass
        requestCode("ana@x.com");
        clock.advance(Duration.ofSeconds(20));

        // When / Then: the resend gets 429 with 40 s to wait
        requestCode("ana@x.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "40"))
                .andExpect(jsonPath("$.message").value(containsString("40 seconds")));
    }

    /**
     * Proves one email gets at most 5 codes in a rolling hour and is let back in once the hour
     * passes, so an inbox can't be flooded but a real user isn't locked out for good.
     * <p>
     * Cases: 5 requests 61 s apart → accepted; 6th → 429; one hour later → accepted
     * (specs/001-request-code/spec.md User Story 2, FR-008;
     * specs/001-request-code/research.md R5).
     */
    @Test
    void sixthRequestInAnHourIsRefused() throws Exception {
        // Given: 5 accepted requests, 61 s apart so the cooldown never blocks them
        for (int i = 0; i < 5; i++) {
            requestCode("ana@x.com").andExpect(status().isAccepted());
            clock.advance(Duration.ofSeconds(61));
        }

        // When / Then: the 6th is refused
        requestCode("ana@x.com").andExpect(status().isTooManyRequests());
        // Given: an hour passes, so the earlier requests leave the window
        clock.advance(Duration.ofHours(1));
        // When / Then: the same email is accepted again
        requestCode("ana@x.com").andExpect(status().isAccepted());
    }
}
