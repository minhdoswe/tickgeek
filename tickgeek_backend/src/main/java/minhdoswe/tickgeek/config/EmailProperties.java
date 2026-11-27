package minhdoswe.tickgeek.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the code email job: how many attempts in total, and the wait before the first retry,
 * which doubles after each failure (specs/002-sending-code/research.md R6, R8).
 */
@ConfigurationProperties("tickgeek.email")
public record EmailProperties(int maxAttempts, Duration retryBaseDelay) {
}
