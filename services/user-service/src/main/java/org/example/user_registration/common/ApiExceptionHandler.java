package org.example.user_registration.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ResourceNotFoundException exception) {
        logger.warn("Request failed: resource not found ({})", exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("code", "RESOURCE_NOT_FOUND", "message", exception.getMessage()));
    }

    @ExceptionHandler({EmailAlreadyExistsException.class, DataIntegrityViolationException.class})
    public ResponseEntity<Map<String, String>> handleConflict(Exception exception) {
        logger.warn("Request failed: resource conflict ({})", exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("code", "CONFLICT", "message", exception instanceof EmailAlreadyExistsException
                        ? exception.getMessage() : "The request conflicts with an existing resource"));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleUnauthorized(Exception exception) {
        logger.warn("Request failed: unauthorized ({})", exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("code", "UNAUTHORIZED", "message", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException exception) {
        logger.warn("Request failed: validation error");
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Request validation failed");
        return ResponseEntity.badRequest().body(Map.of("code", "VALIDATION_ERROR", "message", message));
    }
}
