package minhdoswe.tickgeek.module.email;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import minhdoswe.tickgeek.config.EmailProperties;

/**
 * The two short transactions around one send attempt: claim before the send, record the result
 * after it. The send itself runs outside both, so no connection or lock is held during a provider
 * call (specs/002-sending-code/research.md R2, R3;
 * .claude/skills/supabase-postgres-best-practices/references/lock-short-transactions.md).
 */
@Component
public class OutboxJobSteps {

    static final String MAX_ATTEMPTS_REACHED = "max attempts reached";

    private final OutboxRepository outboxes;
    private final OutboxJobOwner owner;
    private final EmailProperties properties;
    private final ApplicationEventPublisher events;

    public OutboxJobSteps(OutboxRepository outboxes, OutboxJobOwner owner, EmailProperties properties,
            ApplicationEventPublisher events) {
        this.outboxes = outboxes;
        this.owner = owner;
        this.properties = properties;
        this.events = events;
    }

    /**
     * Claims the next attempt and returns the payload to send. Returns nothing when the job is
     * finished, no longer wanted (then it is cancelled), not due yet, or out of attempts (then
     * it gives up). The checks run in that order.
     */
    @Transactional
    public Optional<String> claim(UUID outboxId, Instant now) {
        Outbox job = outboxes.findById(outboxId).orElse(null);
        if (job == null || !job.isPending()) {
            return Optional.empty();
        }
        if (!owner.isStillWanted(outboxId)) {
            job.cancel();
            return Optional.empty();
        }
        if (job.getNextAttemptAt().isAfter(now)) {
            return Optional.empty();
        }
        if (job.getAttempts() >= properties.maxAttempts()) {
            abandon(job, MAX_ATTEMPTS_REACHED);
            return Optional.empty();
        }
        job.claim();
        return Optional.of(job.getPayload());
    }

    /** A provider accepted at {@code now}: the job is sent and its owner is told, together. */
    @Transactional
    public void recordSuccess(UUID outboxId, Instant now) {
        Outbox job = load(outboxId);
        if (!job.isPending()) {
            return;
        }
        job.succeed(now);
        owner.delivered(outboxId, now);
    }

    /**
     * The attempt failed at {@code now}: with retries left the job is rescheduled and announced
     * again, and the announcement runs after this transaction commits. After the last allowed
     * attempt it gives up (specs/002-sending-code/research.md R1, R6).
     */
    @Transactional
    public void recordFailure(UUID outboxId, Instant now, String error) {
        Outbox job = load(outboxId);
        if (!job.isPending()) {
            return;
        }
        if (job.getAttempts() >= properties.maxAttempts()) {
            abandon(job, error);
            return;
        }
        job.retryLater(now, error, properties.retryBaseDelay());
        events.publishEvent(new OutboxJobScheduled(outboxId, Duration.between(now, job.getNextAttemptAt())));
    }

    /** The job fails for good and its owner is told, together (data-model.md "abandon"). */
    private void abandon(Outbox job, String error) {
        job.giveUp(error);
        owner.abandoned(job.getId());
    }

    private Outbox load(UUID outboxId) {
        return outboxes.findById(outboxId).orElseThrow();
    }
}
