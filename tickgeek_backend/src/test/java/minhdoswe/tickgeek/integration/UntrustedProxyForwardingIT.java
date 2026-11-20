package minhdoswe.tickgeek.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import minhdoswe.tickgeek.support.AbstractIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A client cannot choose its own source IP when it is not a trusted proxy
 * (specs/001-request-code/research.md R6, R14).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "server.tomcat.remoteip.internal-proxies=10\\.255\\.255\\.1")
class UntrustedProxyForwardingIT extends AbstractIntegrationTest {

    @LocalServerPort
    int port;

    /**
     * Proves a caller that isn't a trusted proxy can't choose its own IP with X-Forwarded-For, so
     * an attacker can't dodge the per-IP limit by sending a new fake IP each time.
     * <p>
     * Cases: localhost, untrusted here (see @TestPropertySource), sends X-Forwarded-For → 202,
     * code stored under its real address 127.0.0.1 (specs/001-request-code/research.md R6, R14).
     */
    @Test
    void spoofedForwardedForIsIgnored() throws Exception {
        // When: localhost, which is not trusted here (see @TestPropertySource), sends a spoofed X-Forwarded-For
        assertThat(ForwardedHeaderSupport.requestCodeWithForwardedFor(port, "ana@x.com")).isEqualTo(202);

        // Then: the code is stored under the real address 127.0.0.1
        assertThat(jdbc.queryForObject("SELECT request_source FROM verification_codes", String.class))
                .isEqualTo("127.0.0.1");
    }
}
