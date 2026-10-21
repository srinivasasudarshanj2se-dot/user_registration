package org.example.user_registration.auth;

import org.example.user_registration.auth.AuthDtos.LoginRequest;
import org.example.user_registration.auth.AuthDtos.RegisterRequest;
import org.example.user_registration.common.EmailAlreadyExistsException;
import org.example.user_registration.common.InvalidRefreshTokenException;
import org.example.user_registration.common.UserServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;
import java.util.List;

@Component
public class UserServiceClient {
    private final RestClient restClient;

    public UserServiceClient(@Value("${user-service.base-url}") String baseUrl,
                             @Value("${user-service.api-key}") String apiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("X-Internal-Api-Key", apiKey)
                .build();
    }

    public void register(RegisterRequest request) {
        try {
            restClient.post()
                    .uri("/internal/auth/users")
                    .body(new InternalRegisterRequest(request.firstName(), request.lastName(), request.email(),
                            request.mobile(), request.password()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                throw new EmailAlreadyExistsException(request.email().trim());
            }
            throw serviceFailure(exception);
        } catch (ResourceAccessException exception) {
            throw new UserServiceUnavailableException(exception);
        }
    }

    public UserIdentity verifyCredentials(LoginRequest request) {
        try {
            UserIdentity identity = restClient.post()
                    .uri("/internal/auth/verify")
                    .body(new CredentialsRequest(request.email(), request.password()))
                    .retrieve()
                    .body(UserIdentity.class);
            if (identity == null) {
                throw new UserServiceUnavailableException();
            }
            return identity;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
                throw new org.springframework.security.authentication.BadCredentialsException(
                        "Email or password is incorrect");
            }
            throw serviceFailure(exception);
        } catch (ResourceAccessException exception) {
            throw new UserServiceUnavailableException(exception);
        }
    }

    public UserIdentity getUser(String uuid) {
        try {
            UserIdentity identity = restClient.get()
                    .uri("/internal/auth/users/{uuid}", uuid)
                    .retrieve()
                    .body(UserIdentity.class);
            if (identity == null) {
                throw new UserServiceUnavailableException();
            }
            return identity;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new InvalidRefreshTokenException();
            }
            throw serviceFailure(exception);
        } catch (ResourceAccessException exception) {
            throw new UserServiceUnavailableException(exception);
        }
    }

    private static RuntimeException serviceFailure(RestClientResponseException exception) {
        if (exception.getStatusCode().is5xxServerError()) {
            return new UserServiceUnavailableException(exception);
        }
        if (exception.getStatusCode().value() == HttpStatus.FORBIDDEN.value()) {
            return new UserServiceUnavailableException(exception);
        }
        return new IllegalStateException(
                "User service rejected an internal request (HTTP " + exception.getStatusCode().value() + ")");
    }

    public record UserIdentity(String uuid, String email, List<String> roles) {
    }

    private record InternalRegisterRequest(String firstName, String lastName, String email,
                                           String mobile, String password) {
    }

    private record CredentialsRequest(String email, String password) {
    }
}
