package minhdoswe.tickgeek.module.email;

/** What an unsent code email job carries; erased from the outbox once the job finishes. */
public record CodeEmailPayload(String to, String code) {
}
