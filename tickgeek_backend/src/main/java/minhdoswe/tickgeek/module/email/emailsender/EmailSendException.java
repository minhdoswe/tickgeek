package minhdoswe.tickgeek.module.email.emailsender;

/**
 * A provider did not accept an email. The message is stored as the job's last error, so it must
 * never hold the payload, the code or the recipient address
 * (specs/002-sending-code/contracts/email-job.md).
 */
public class EmailSendException extends RuntimeException {

    public EmailSendException(String message) {
        super(message);
    }
}
