package minhdoswe.tickgeek.common;

import java.util.OptionalLong;

import org.springframework.http.HttpStatus;

public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String error;

    protected ApiException(HttpStatus status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }

    public HttpStatus status() {
        return status;
    }

    public String error() {
        return error;
    }

    public OptionalLong retryAfterSeconds() {
        return OptionalLong.empty();
    }
}
