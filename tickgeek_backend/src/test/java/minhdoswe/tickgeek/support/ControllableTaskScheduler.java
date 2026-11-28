package minhdoswe.tickgeek.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;

import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * A task scheduler for tests that follows the test clock. A task due now runs at once on the pool;
 * a task due later is held until the test moves the clock and calls {@link #runDue()}, which runs
 * it on the test thread. That keeps retries deterministic: no attempt runs unless the test asks
 * for it. Held tasks get no future (null), which the email job listener ignores.
 */
public class ControllableTaskScheduler extends ThreadPoolTaskScheduler {

    private final Clock clock;
    private final List<HeldTask> held = new CopyOnWriteArrayList<>();

    private record HeldTask(Runnable task, Instant dueAt) {
    }

    public ControllableTaskScheduler(Clock clock) {
        this.clock = clock;
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
        if (startTime.isAfter(clock.instant())) {
            held.add(new HeldTask(task, startTime));
            return null;
        }
        return super.schedule(task, Instant.now());
    }

    /**
     * Never starts a repeating task such as the outbox sweeper, so it can't run in the background
     * during a test; tests call it directly.
     */
    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
        return null;
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) {
        return null;
    }

    /** Runs, on the calling thread, every held task that is due by the test clock. */
    public void runDue() {
        for (HeldTask next : held) {
            if (!next.dueAt().isAfter(clock.instant()) && held.remove(next)) {
                next.task().run();
            }
        }
    }

    public void clear() {
        held.clear();
    }
}
