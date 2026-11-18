package minhdoswe.tickgeek.module.auth.limit;

import java.time.Instant;

/** One code request: the normalized email, the client source, and the request time. */
public record CodeRequestContext(String email, String source, Instant now) {
}
