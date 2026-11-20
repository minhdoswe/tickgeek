package minhdoswe.tickgeek.module.auth.limit;

import java.time.Instant;

public record CodeRequestContext(String email, String source, Instant now) {
}
