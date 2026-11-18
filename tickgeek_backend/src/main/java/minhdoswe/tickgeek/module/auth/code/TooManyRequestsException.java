package minhdoswe.tickgeek.module.auth.code;

import java.util.OptionalLong;

import org.springframework.http.HttpStatus;

import minhdoswe.tickgeek.common.ApiException;

public class TooManyRequestsException extends ApiException {

    public static final String CODE = "TOO_MANY_REQUESTS";
    public static final String GENERIC_MESSAGE = "Too many requests. Please try again later.";

    private final OptionalLong retryAfterSeconds;                      

    private TooManyRequestsException(String message, OptionalLong retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, CODE, message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static TooManyRequestsException cooldown(long retryAfterSeconds) {  
        String message = "Please wait %d seconds before requesting another code.".formatted(retryAfterSeconds);
        return new TooManyRequestsException(message, OptionalLong.of(retryAfterSeconds));
    }

    /** Sets no Retry-After, so the response does not reveal when the limit resets (contracts/auth-api.md). */
    public static TooManyRequestsException generic() {
        return new TooManyRequestsException(GENERIC_MESSAGE, OptionalLong.empty());  
    }

    @Override
    public OptionalLong retryAfterSeconds() {               
        return retryAfterSeconds;
    }
}