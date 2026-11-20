package minhdoswe.tickgeek.module.auth.limit;

public record LimitViolation(String limitName, long retryAfterSeconds, boolean disclosesWait) {
}
