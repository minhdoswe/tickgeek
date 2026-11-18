package minhdoswe.tickgeek.module.email;

import java.time.Duration;
import java.util.UUID;

/**
 * "This outbox job should run after the delay", announced inside a transaction (research R21).
 * It carries no email or code. Nothing listens for it yet: the sending side is not built.
 */
public record OutboxJobScheduled(UUID outboxId, Duration delay) {

    public static OutboxJobScheduled now(UUID outboxId) {
        return new OutboxJobScheduled(outboxId, Duration.ZERO);
    }
}
