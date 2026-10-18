package org.example.user_registration.auth;

import jakarta.validation.Valid;
import org.example.user_registration.auth.AuthDtos.LoginRequest;
import org.example.user_registration.auth.AuthDtos.RefreshRequest;
import org.example.user_registration.auth.AuthDtos.RegisterRequest;
import org.example.user_registration.auth.AuthDtos.RegisterResponse;
import org.example.user_registration.auth.AuthDtos.TokenResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return new RegisterResponse(true, "User Registered Successfully");
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh-token")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }
}
