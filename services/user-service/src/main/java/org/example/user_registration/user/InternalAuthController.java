package org.example.user_registration.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.example.user_registration.common.ResourceNotFoundException;
import org.example.user_registration.user.UserDtos.UserRequest;
import org.example.user_registration.user.UserDtos.UserResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import io.swagger.v3.oas.annotations.Hidden;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Hidden
@RestController
@RequestMapping("/internal/auth")
public class InternalAuthController {
    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final byte[] apiKey;

    public InternalAuthController(UserService userService, UserRepository userRepository,
                                  PasswordEncoder passwordEncoder,
                                  @Value("${internal.api-key}") String apiKey) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        if (apiKey.isBlank()) {
            throw new IllegalStateException("internal.api-key must not be blank");
        }
        this.apiKey = apiKey.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@RequestHeader("X-Internal-Api-Key") String suppliedKey,
                                 @Valid @RequestBody UserRequest request) {
        authorize(suppliedKey);
        return userService.create(request);
    }

    @PostMapping("/verify")
    public AuthUserResponse verifyCredentials(@RequestHeader("X-Internal-Api-Key") String suppliedKey,
                                              @Valid @RequestBody CredentialsRequest request) {
        authorize(suppliedKey);
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .filter(User::getEnabled)
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPassword()))
                .orElseThrow(() -> new BadCredentialsException("Email or password is incorrect"));
        return AuthUserResponse.from(user);
    }

    @GetMapping("/users/{uuid}")
    public AuthUserResponse getUser(@RequestHeader("X-Internal-Api-Key") String suppliedKey,
                                    @PathVariable String uuid) {
        authorize(suppliedKey);
        User user = userRepository.findByUuid(uuid)
                .filter(User::getEnabled)
                .orElseThrow(() -> new ResourceNotFoundException("User was not found"));
        return AuthUserResponse.from(user);
    }

    private void authorize(String suppliedKey) {
        if (!MessageDigest.isEqual(apiKey, suppliedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
    }

    public record CredentialsRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record AuthUserResponse(String uuid, String email, List<String> roles) {
        static AuthUserResponse from(User user) {
            return new AuthUserResponse(user.getUuid(), user.getEmail(),
                    user.getRoles().stream().map(Role::getName).toList());
        }
    }
}
