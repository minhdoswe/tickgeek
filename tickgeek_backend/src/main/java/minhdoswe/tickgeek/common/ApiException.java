package minhdoswe.tickgeek.common;

import java.util.OptionalLong;

import org.springframework.http.HttpStatus;

/** A failure with a fixed HTTP status and contract error code; subclasses define each case. */
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

    /** Seconds the client should wait before retrying; empty unless the subclass says otherwise. */
    public OptionalLong retryAfterSeconds() {
        return OptionalLong.empty();
    }
}
