package minhdoswe.tickgeek.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the code email job: how many attempts in total, the wait before the first retry,
 * which doubles after each failure, how long a claimed attempt keeps others away, and how often
 * the sweeper looks for due jobs (specs/002-sending-code/research.md R1, R6, R8).
 */
@ConfigurationProperties("tickgeek.email")
public record EmailProperties(int maxAttempts, Duration retryBaseDelay, Duration claimLease, Duration sweepInterval) {
}
