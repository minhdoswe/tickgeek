package minhdoswe.tickgeek.config;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthPropertiesTest {

    private static final String WEAK = "x".repeat(AuthProperties.MIN_SECRET_BYTES - 1);

    /**
     * Proves the app refuses to start with a weak or missing code secret, so someone holding a
     * leaked database can't brute-force the key and recover the codes.
     * <p>
     * Cases: 31-byte secret → startup error; no secret → startup error
     * (specs/001-request-code/research.md R3; tickgeek_backend/docs/deployment.md).
     */
    @Test
    void rejectsShortSecrets() {
        // When / Then: a 31-byte secret is rejected
        assertThatThrownBy(() -> properties(WEAK)).hasMessageContaining("code-secret");
        // When / Then: a missing secret is rejected
        assertThatThrownBy(() -> properties(null)).hasMessageContaining("code-secret");
    }

    private static AuthProperties properties(String codeSecret) {
        return new AuthProperties(Duration.ofMinutes(10), null, codeSecret);
    }
}
