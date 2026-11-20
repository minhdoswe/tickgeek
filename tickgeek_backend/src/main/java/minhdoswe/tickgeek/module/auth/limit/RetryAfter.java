package minhdoswe.tickgeek.module.auth.limit;

import java.time.Duration;
import java.time.Instant;

public final class RetryAfter {

    private static final long MIN_SECONDS = 1;

    private RetryAfter() {
    }

    public static long secondsUntil(Instant now, Instant moment) {
        Duration wait = Duration.between(now, moment);
        long seconds = wait.getSeconds() + (wait.getNano() > 0 ? 1 : 0);
        return Math.max(MIN_SECONDS, seconds);
    }
}
