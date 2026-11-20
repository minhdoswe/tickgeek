package minhdoswe.tickgeek.module.auth.dto;

import java.time.Instant;

public record CodeRequestResponse(String email, long codeValidMinutes, Instant resendAvailableAt) {
}
