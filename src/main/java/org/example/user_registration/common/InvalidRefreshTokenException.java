package org.example.user_registration.common;

public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
        super("Refresh token is invalid or expired");
    }
}
