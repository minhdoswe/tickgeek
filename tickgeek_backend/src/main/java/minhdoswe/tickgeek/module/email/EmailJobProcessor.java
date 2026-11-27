package minhdoswe.tickgeek.module.email;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import minhdoswe.tickgeek.module.email.emailsender.EmailSender;

/**
 * Runs one send attempt: claim, send with no transaction open, record the result. Not
 * transactional itself, so the claim commits before the provider is called
 * (specs/002-sending-code/research.md R2).
 */
@Component
public class EmailJobProcessor {

    private final OutboxJobSteps steps;
    private final EmailSender sender;
    private final CodeEmailComposer composer;
    private final Clock clock;

    public EmailJobProcessor(OutboxJobSteps steps, EmailSender sender, CodeEmailComposer composer, Clock clock) {
        this.steps = steps;
        this.sender = sender;
        this.composer = composer;
        this.clock = clock;
    }

    public void process(UUID outboxId) {
        Optional<String> payload = steps.claim(outboxId, clock.instant());
        if (payload.isEmpty()) {
            return;
        }
        try {
            sender.send(payload.get(), composer.recipientOf(payload.get()));
        } catch (RuntimeException failure) {
            steps.recordFailure(outboxId, clock.instant(), describe(failure));
            return;
        }
        steps.recordSuccess(outboxId, clock.instant());
    }

    /** The failure's type and message only; senders keep the payload out of their messages. */
    private static String describe(RuntimeException failure) {
        return failure.getClass().getSimpleName() + ": " + failure.getMessage();
    }
}
