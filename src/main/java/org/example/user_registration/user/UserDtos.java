package org.example.user_registration.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class UserDtos {
    private UserDtos() {
    }

    public record UserRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Pattern(regexp = "^[+0-9() -]{7,20}$") String mobile,
            @NotBlank @Size(min = 8, max = 72) String password
    ) {
    }

    public record UserUpdateRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Pattern(regexp = "^[+0-9() -]{7,20}$") String mobile
    ) {
    }

    public record UserResponse(Long id, String uuid, String firstName, String lastName, String email,
                               String mobile, Boolean enabled, Instant createdAt, Instant updatedAt) {
        public static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getUuid(), user.getFirstName(), user.getLastName(),
                    user.getEmail(), user.getMobile(), user.getEnabled(), user.getCreatedAt(), user.getUpdatedAt());
        }
    }
}
