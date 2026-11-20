package minhdoswe.tickgeek.module.email;

import java.time.Duration;
import java.util.UUID;

public record OutboxJobScheduled(UUID outboxId, Duration delay) {

    public static OutboxJobScheduled now(UUID outboxId) {
        return new OutboxJobScheduled(outboxId, Duration.ZERO);
    }
}
