package com.hbsisms.common.exception;

import java.util.List;
import org.springframework.http.HttpStatus;

/** Business error with an HTTP status and a message that is safe to show to the client. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final List<?> shortages;

    public ApiException(HttpStatus status, String message) {
        this(status, message, null);
    }

    public ApiException(HttpStatus status, String message, List<?> shortages) {
        super(message);
        this.status = status;
        this.shortages = shortages == null ? null : List.copyOf(shortages);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public List<?> getShortages() {
        return shortages;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    public static ApiException conflict(String message, List<?> shortages) {
        return new ApiException(HttpStatus.CONFLICT, message, shortages);
    }

    public static ApiException tooManyRequests(String message) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, message);
    }
}
