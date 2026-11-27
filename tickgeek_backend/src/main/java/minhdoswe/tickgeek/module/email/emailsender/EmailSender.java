package minhdoswe.tickgeek.module.email.emailsender;

/**
 * Hands one email to a provider. Returning normally means a provider accepted it; any exception
 * means the attempt failed and will be retried. Implementations must never put the payload or the
 * code in an exception message or a log line, except the local-only logging sender
 * (specs/002-sending-code/contracts/email-job.md).
 */
public interface EmailSender {

    void send(String payload, String to);
}
