package org.example.user_registration.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Pattern(regexp = "^[+0-9() -]{7,20}$") String mobile,
            @NotBlank @Size(min = 8, max = 72) String password
    ) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record RegisterResponse(boolean success, String message) {
    }

    public record TokenResponse(String accessToken, String refreshToken, long expiresIn) {
    }
}
