package minhdoswe.tickgeek.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FrontendPropertiesTest {

    /**
     * Proves the app refuses to start with a weak or missing frontend secret, so nobody can
     * guess it and name their own client IP to dodge the per-IP limit.
     * <p>
     * Cases: 31-byte secret → startup error; no secret → startup error
     * (specs/001-request-code/research.md R6).
     */
    @Test
    void rejectsShortSecrets() {
        // When / Then: a 31-byte secret is rejected
        assertThatThrownBy(() -> new FrontendProperties("x".repeat(AuthProperties.MIN_SECRET_BYTES - 1)))
                .hasMessageContaining("tickgeek.frontend.secret");
        // When / Then: a missing secret is rejected
        assertThatThrownBy(() -> new FrontendProperties(null)).hasMessageContaining("tickgeek.frontend.secret");
    }
}
