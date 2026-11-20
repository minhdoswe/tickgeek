package minhdoswe.tickgeek.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import minhdoswe.tickgeek.support.AbstractIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/** With the default settings the Next.js server on localhost is trusted to forward the client IP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TrustedProxyForwardingIT extends AbstractIntegrationTest {

    @LocalServerPort
    int port;

    /**
     * Proves requests through the trusted Next.js server are counted under the real client IP, so
     * the per-IP limit blocks one noisy client instead of every user behind the frontend.
     * <p>
     * Cases: localhost (trusted by default) sends X-Forwarded-For: 1.2.3.4 → 202, code stored
     * under 1.2.3.4 (specs/001-request-code/research.md R6, R14).
     */
    @Test
    void forwardedForFromTrustedProxyIsUsed() throws Exception {
        // When: localhost sends a real HTTP request with X-Forwarded-For: 1.2.3.4
        assertThat(ForwardedHeaderSupport.requestCodeWithForwardedFor(port, "ana@x.com")).isEqualTo(202);

        // Then: the code is stored under the forwarded IP
        assertThat(jdbc.queryForObject("SELECT request_source FROM verification_codes", String.class))
                .isEqualTo(ForwardedHeaderSupport.SPOOFED_IP);
    }
}
