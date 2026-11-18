package minhdoswe.tickgeek.common;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/** The single error body shape returned by every endpoint (contracts/auth-api.md). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String error, String message, Map<String, List<String>> fields) {

    public static ErrorResponse of(String error, String message) {
        return new ErrorResponse(error, message, null);
    }
}
