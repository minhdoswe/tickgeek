package minhdoswe.tickgeek.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** A clock tests can set and move forward. */
public class MutableClock extends Clock {

    public static final Instant START = Instant.parse("2026-11-11T10:00:00Z");

    private final AtomicReference<Instant> now = new AtomicReference<>(START);

    public void reset() {
        now.set(START);
    }

    public void advance(Duration duration) {
        now.updateAndGet(current -> current.plus(duration));
    }

    @Override
    public Instant instant() {
        return now.get();
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
