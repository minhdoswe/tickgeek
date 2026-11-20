package minhdoswe.tickgeek.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import minhdoswe.tickgeek.support.AbstractIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Only the frontend, proven by the shared secret, can name the client IP
 * (specs/001-request-code/research.md R6).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FrontendClientIpIT extends AbstractIntegrationTest {

    private static final String WRONG_SECRET = "wrong-frontend-secret-at-least-32-bytes";

    @LocalServerPort
    int port;

    /**
     * Proves requests sent by the frontend are counted under the real client IP, so the per-IP
     * limit blocks one noisy client instead of every user behind the frontend's shared hosting
     * addresses.
     * <p>
     * Cases: right secret with client IP 1.2.3.4 → 202, code stored under 1.2.3.4
     * (specs/001-request-code/research.md R6, R14).
     */
    @Test
    void clientIpFromFrontendIsUsed() throws Exception {
        // When: the frontend sends the client IP with the right secret
        assertThat(ForwardedHeaderSupport.requestCodeAsFrontend(port, "ana@x.com", "1.2.3.4", FRONTEND_SECRET))
                .isEqualTo(202);

        // Then: the code is stored under the client IP
        assertThat(storedSource()).isEqualTo("1.2.3.4");
    }

    /**
     * Proves a caller without the secret can't choose its own IP, so an attacker can't dodge the
     * per-IP limit by sending a new fake IP each time.
     * <p>
     * Cases: wrong secret with client IP 1.2.3.4 → 202, code stored under the caller's real
     * address 127.0.0.1 (specs/001-request-code/research.md R6, R14).
     */
    @Test
    void clientIpWithWrongSecretIsIgnored() throws Exception {
        // When: a caller sends a client IP with the wrong secret
        assertThat(ForwardedHeaderSupport.requestCodeAsFrontend(port, "ana@x.com", "1.2.3.4", WRONG_SECRET))
                .isEqualTo(202);

        // Then: the code is stored under the caller's real address
        assertThat(storedSource()).isEqualTo("127.0.0.1");
    }

    /**
     * Proves a client IP the frontend couldn't determine is counted as one shared "unknown"
     * source, so those requests are still rate-limited instead of skipping the per-IP limit.
     * <p>
     * Cases: right secret with client IP "unknown" → 202, code stored under unknown
     * (specs/001-request-code/research.md R6).
     */
    @Test
    void unknownClientIpFromFrontendIsStoredAsUnknown() throws Exception {
        // When: the frontend sends "unknown" as the client IP with the right secret
        assertThat(ForwardedHeaderSupport.requestCodeAsFrontend(port, "ana@x.com", "unknown", FRONTEND_SECRET))
                .isEqualTo(202);

        // Then: the code is stored under unknown
        assertThat(storedSource()).isEqualTo("unknown");
    }

    private String storedSource() {
        return jdbc.queryForObject("SELECT request_source FROM verification_codes", String.class);
    }
}
