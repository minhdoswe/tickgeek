package minhdoswe.tickgeek.module.auth.limit.implement.hourlylimit;

import minhdoswe.tickgeek.module.auth.limit.CodeRequestContext;
import minhdoswe.tickgeek.module.auth.limit.CodeRequestLimit;
import minhdoswe.tickgeek.module.auth.limit.LimitViolation;
import minhdoswe.tickgeek.module.auth.limit.RetryAfter;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Refuses a request when the requests already made in the rolling window reach the maximum.
 * Subclasses choose which requests count (same email, same source, or all).
 */
public abstract class HourlyLimit implements CodeRequestLimit {

    private final Duration window;

    protected HourlyLimit(Duration window) {
        this.window = window;
    }

    protected abstract String name();

    protected abstract int maximum();

    protected abstract long countSince(CodeRequestContext context, Instant since);

    protected abstract Optional<Instant> oldestSince(CodeRequestContext context, Instant since);

    @Override
    public Optional<LimitViolation> check(CodeRequestContext context) {
        Instant since = context.now().minus(window);
        if (countSince(context, since) < maximum()) {
            return Optional.empty();
        }
        Instant freedAt = oldestSince(context, since).orElse(since).plus(window);
        return Optional.of(new LimitViolation(name(), RetryAfter.secondsUntil(context.now(), freedAt), false));
    }
}
