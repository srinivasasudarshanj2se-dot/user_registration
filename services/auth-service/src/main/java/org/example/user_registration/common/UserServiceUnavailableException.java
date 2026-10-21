package org.example.user_registration.common;

public class UserServiceUnavailableException extends RuntimeException {
    public UserServiceUnavailableException() {
        super("User service is currently unavailable");
    }

    public UserServiceUnavailableException(Throwable cause) {
        super("User service is currently unavailable", cause);
    }
}
