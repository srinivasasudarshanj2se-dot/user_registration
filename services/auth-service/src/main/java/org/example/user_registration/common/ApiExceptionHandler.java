package org.example.user_registration.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleConflict(EmailAlreadyExistsException exception) {
        logger.warn("Authentication request failed: resource conflict");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("code", "CONFLICT", "message", exception.getMessage()));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleConflict(
            org.springframework.dao.DataIntegrityViolationException exception) {
        logger.warn("Authentication request failed: data conflict");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("code", "CONFLICT", "message", "The request conflicts with an existing resource"));
    }

    @ExceptionHandler({BadCredentialsException.class, InvalidRefreshTokenException.class})
    public ResponseEntity<Map<String, String>> handleUnauthorized(Exception exception) {
        logger.warn("Authentication request failed: unauthorized ({})", exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("code", "UNAUTHORIZED", "message", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException exception) {
        logger.warn("Authentication request failed: validation error");
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Request validation failed");
        return ResponseEntity.badRequest().body(Map.of("code", "VALIDATION_ERROR", "message", message));
    }

    @ExceptionHandler(UserServiceUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleUnavailable(Exception exception) {
        logger.error("Authentication dependency unavailable ({})", exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("code", "DEPENDENCY_UNAVAILABLE", "message", "Please retry the request later"));
    }
}
