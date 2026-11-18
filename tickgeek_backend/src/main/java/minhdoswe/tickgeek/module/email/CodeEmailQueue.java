package minhdoswe.tickgeek.module.email;

import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Queues a code email: saves the outbox job in the caller's transaction and announces it
 * (research R21). Sending the email is not part of this branch.
 */
@Component
public class CodeEmailQueue {

    private final OutboxRepository outboxes;
    private final ApplicationEventPublisher events;

    public CodeEmailQueue(OutboxRepository outboxes, ApplicationEventPublisher events) {
        this.outboxes = outboxes;
        this.events = events;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(Long verificationCodeId, String payload, Instant now) {
        Outbox job = outboxes.save(Outbox.forCode(verificationCodeId, payload, now));
        events.publishEvent(OutboxJobScheduled.now(job.getId()));
    }
}
