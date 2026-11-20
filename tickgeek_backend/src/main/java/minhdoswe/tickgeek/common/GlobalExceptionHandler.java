package minhdoswe.tickgeek.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    private static final String VALIDATION_MESSAGE = "Please check the highlighted fields.";
    private static final String UNREADABLE_MESSAGE = "The request body is not valid JSON.";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, List<String>> fields = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.groupingBy(FieldError::getField, LinkedHashMap::new,
                        Collectors.mapping(FieldError::getDefaultMessage, Collectors.toList())));
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(VALIDATION_FAILED, VALIDATION_MESSAGE, fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(VALIDATION_FAILED, UNREADABLE_MESSAGE));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException exception) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(exception.status());
        exception.retryAfterSeconds()
                .ifPresent(seconds -> response.header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds)));
        return response.body(ErrorResponse.of(exception.error(), exception.getMessage()));
    }
}
