package minhdoswe.tickgeek.module.auth.limit;

/**
 * A refused code request. Only the resend cooldown may tell the user how long to wait; the other
 * limits get a generic message so they reveal nothing about other requests.
 */
public record LimitViolation(String limitName, long retryAfterSeconds, boolean disclosesWait) {
}
