package com.hbsisms.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Error body. The frontend reads {@code message}; {@code errors} maps field name to validation message. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(int status, String message, Map<String, String> errors, String path, Instant timestamp) {

    public static ErrorResponse of(HttpStatus status, String message, String path) {
        return new ErrorResponse(status.value(), message, null, path, Instant.now());
    }

    public static ErrorResponse of(HttpStatus status, String message, Map<String, String> errors, String path) {
        return new ErrorResponse(status.value(), message, Map.copyOf(errors), path, Instant.now());
    }
}
