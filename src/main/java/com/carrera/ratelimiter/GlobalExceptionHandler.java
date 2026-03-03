package com.carrera.ratelimiter;

import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Handles when an entity is not found → 404
    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(EntityNotFoundException ex) {
        return Map.of("error", "NOT_FOUND", "message", ex.getMessage());
    }

    // Handles @Valid failures → 400 with field-level errors
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        return Map.of("error", "VALIDATION_FAILED", "fields", fieldErrors);
    }

    // Catch-all — logs the full stack trace and returns the cause so it's visible in Postman
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return Map.of(
            "error", "INTERNAL_SERVER_ERROR",
            "type",  ex.getClass().getName(),
            "message", ex.getMessage() != null ? ex.getMessage() : "(no message)"
        );
    }
}