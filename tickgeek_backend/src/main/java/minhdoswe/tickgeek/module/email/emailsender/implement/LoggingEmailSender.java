package minhdoswe.tickgeek.module.email.emailsender.implement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import minhdoswe.tickgeek.module.email.CodeEmailComposer;
import minhdoswe.tickgeek.module.email.emailsender.EmailSender;

/**
 * Local development only: writes the code email to the log instead of sending it, so the code can
 * be read without a real provider. It prints the plaintext code, so it must never run outside the
 * {@code local} profile (specs/002-sending-code/spec.md FR-008, FR-011, FR-012; research.md R7, R9).
 */
@Component
@Profile("local")
public class LoggingEmailSender implements EmailSender {

    static final String PROVIDER = "logging";

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    private final CodeEmailComposer composer;

    public LoggingEmailSender(CodeEmailComposer composer) {
        this.composer = composer;
    }

    @Override
    public void send(String payload, String to) {
        log.info("Code email to {}: {}", to, composer.textOf(payload));
        log.info("Code email accepted by provider {}", PROVIDER);
    }
}
