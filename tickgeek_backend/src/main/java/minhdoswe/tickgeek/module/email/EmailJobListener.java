package minhdoswe.tickgeek.module.email;

import java.time.Clock;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Starts an announced email job once the transaction that announced it has committed, after the
 * job's delay. The schedule lives in memory, so it does not survive a restart
 * (specs/002-sending-code/research.md R1).
 */
@Component
public class EmailJobListener {

    private final TaskScheduler taskScheduler;
    private final EmailJobProcessor processor;
    private final Clock clock;

    public EmailJobListener(TaskScheduler taskScheduler, EmailJobProcessor processor, Clock clock) {
        this.taskScheduler = taskScheduler;
        this.processor = processor;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(OutboxJobScheduled job) {
        taskScheduler.schedule(() -> processor.process(job.outboxId()), clock.instant().plus(job.delay()));
    }
}
