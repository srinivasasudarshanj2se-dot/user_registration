package org.example.user_registration.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ResourceNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("code", "RESOURCE_NOT_FOUND", "message", exception.getMessage()));
    }

    @ExceptionHandler({EmailAlreadyExistsException.class, DataIntegrityViolationException.class})
    public ResponseEntity<Map<String, String>> handleConflict(Exception exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("code", "CONFLICT", "message", exception instanceof EmailAlreadyExistsException
                        ? exception.getMessage() : "The request conflicts with an existing resource"));
    }

    @ExceptionHandler({BadCredentialsException.class, InvalidRefreshTokenException.class})
    public ResponseEntity<Map<String, String>> handleUnauthorized(Exception exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("code", "UNAUTHORIZED", "message", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Request validation failed");
        return ResponseEntity.badRequest().body(Map.of("code", "VALIDATION_ERROR", "message", message));
    }
}
