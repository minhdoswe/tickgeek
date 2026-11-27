package minhdoswe.tickgeek.module.email;

import java.time.Instant;
import java.util.UUID;

/**
 * What the email job asks of whoever owns an outbox row, so the email module never needs to know
 * what the email is for. The auth module implements it for code emails. Every method runs inside
 * the job's transaction, so the outbox change and the owner's change commit together
 * (specs/002-sending-code/research.md R4; contracts/email-job.md).
 */
public interface OutboxJobOwner {

    /** Whether the email is still worth sending; false cancels the job before any attempt. */
    boolean isStillWanted(UUID outboxId);

    /** A provider accepted the email at {@code acceptedAt}. */
    void delivered(UUID outboxId, Instant acceptedAt);

    /** The job gave up; the email will never be sent. */
    void abandoned(UUID outboxId);
}
