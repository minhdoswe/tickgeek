package minhdoswe.tickgeek.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tickgeek.auth")
public record AuthProperties(
        Duration codeTtl,
        Limits limits,
        String codeSecret) {

    public static final int MIN_SECRET_BYTES = 32;

    public AuthProperties {
        requireStrongSecret("tickgeek.auth.code-secret", codeSecret);
    }

    public long codeValidMinutes() {
        return codeTtl.toMinutes();
    }

    private static void requireStrongSecret(String name, String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(name + " must be at least " + MIN_SECRET_BYTES + " bytes");
        }
    }

    public record Limits(
            Duration window,
            Duration resendCooldown,
            int perEmailPerHour,
            int perSourcePerHour,
            int globalPerHour) {
    }
}
