package minhdoswe.tickgeek.support;

import java.time.Duration;

import minhdoswe.tickgeek.config.AuthProperties;
import minhdoswe.tickgeek.config.EmailProperties;

/** The production defaults from application.yaml, for unit tests. */
public final class TestProperties {

    private TestProperties() {
    }

    public static AuthProperties auth() {
        return new AuthProperties(
                Duration.ofMinutes(10),
                new AuthProperties.Limits(Duration.ofHours(1), Duration.ofSeconds(60), 5, 20, 1000),
                AbstractIntegrationTest.CODE_SECRET);
    }

    public static EmailProperties email() {
        return new EmailProperties(5, Duration.ofSeconds(5), Duration.ofMinutes(1), Duration.ofMinutes(1));
    }
}
