package minhdoswe.tickgeek.module.email;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs every due PENDING job on a timer, so a job whose in-memory event was lost (restart, crash
 * mid-send once its lease ran out) is still sent. Each job goes through the normal
 * {@link EmailJobProcessor}, whose locked claim keeps it from running twice
 * (specs/002-sending-code/research.md R1). Not lazy, or the timer would never start.
 */
@Component
@Lazy(false)
public class OutboxSweeper {

    static final int BATCH_SIZE = 100;
    private static final Logger log = LoggerFactory.getLogger(OutboxSweeper.class);

    private final OutboxRepository outboxes;
    private final EmailJobProcessor processor;
    private final Clock clock;

    public OutboxSweeper(OutboxRepository outboxes, EmailJobProcessor processor, Clock clock) {
        this.outboxes = outboxes;
        this.processor = processor;
        this.clock = clock;
    }

    /** One pass: up to {@link #BATCH_SIZE} due jobs; one job failing does not stop the rest. */
    @Scheduled(fixedDelayString = "${tickgeek.email.sweep-interval}")
    public void sweep() {
        for (UUID outboxId : outboxes.findDueIds(clock.instant(), BATCH_SIZE)) {
            try {
                processor.process(outboxId);
            } catch (RuntimeException failure) {
                log.warn("Sweeping outbox {} failed: {}", outboxId, failure.toString());
            }
        }
    }
}
