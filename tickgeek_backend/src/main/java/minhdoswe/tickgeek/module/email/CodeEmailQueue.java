package minhdoswe.tickgeek.module.email;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CodeEmailQueue {

    private final OutboxRepository outboxes;
    private final ApplicationEventPublisher events;

    public CodeEmailQueue(OutboxRepository outboxes, ApplicationEventPublisher events) {
        this.outboxes = outboxes;
        this.events = events;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enqueue(String payload, Instant now) {
        Outbox job = outboxes.save(Outbox.pending(payload, now));
        events.publishEvent(OutboxJobScheduled.now(job.getId()));
        return job.getId();
    }
}
