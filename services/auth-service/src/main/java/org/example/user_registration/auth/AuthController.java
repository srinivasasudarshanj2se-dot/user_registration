package org.example.user_registration.auth;

import jakarta.validation.Valid;
import org.example.user_registration.auth.AuthDtos.LoginRequest;
import org.example.user_registration.auth.AuthDtos.RefreshRequest;
import org.example.user_registration.auth.AuthDtos.RegisterRequest;
import org.example.user_registration.auth.AuthDtos.RegisterResponse;
import org.example.user_registration.auth.AuthDtos.TokenResponse;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Register accounts and manage authentication tokens")
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register an account")
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        logger.info("Account registration completed");
        return new RegisterResponse(true, "User Registered Successfully");
    }

    @PostMapping("/login")
    @Operation(summary = "Log in")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        TokenResponse response = authService.login(request);
        logger.info("Authentication completed successfully");
        return response;
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Refresh authentication tokens")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        TokenResponse response = authService.refresh(request);
        logger.info("Authentication tokens refreshed successfully");
        return response;
    }
}
