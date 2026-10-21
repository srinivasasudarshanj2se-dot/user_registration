package org.example.user_registration.auth;

import org.example.user_registration.auth.AuthDtos.LoginRequest;
import org.example.user_registration.auth.AuthDtos.RefreshRequest;
import org.example.user_registration.auth.AuthDtos.RegisterRequest;
import org.example.user_registration.auth.AuthDtos.TokenResponse;
import org.example.user_registration.common.InvalidRefreshTokenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class AuthService {
    private final UserServiceClient userServiceClient;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserServiceClient userServiceClient, RefreshTokenRepository refreshTokenRepository,
                       JwtEncoder jwtEncoder, @Value("${security.jwt.issuer}") String issuer,
                       @Value("${security.jwt.access-token-ttl}") Duration accessTokenTtl,
                       @Value("${security.jwt.refresh-token-ttl}") Duration refreshTokenTtl) {
        this.userServiceClient = userServiceClient;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public void register(RegisterRequest request) {
        userServiceClient.register(request);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        return issueTokens(userServiceClient.verifyCredentials(request));
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hashToken(request.refreshToken()))
                .orElseThrow(InvalidRefreshTokenException::new);
        refreshTokenRepository.delete(existing);
        if (!existing.getExpiryDate().isAfter(Instant.now())) {
            throw new InvalidRefreshTokenException();
        }
        return issueTokens(userServiceClient.getUser(existing.getUserUuid()));
    }

    private TokenResponse issueTokens(UserServiceClient.UserIdentity user) {
        Instant now = Instant.now();
        Instant accessExpiry = now.plus(accessTokenTtl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.uuid())
                .issuedAt(now)
                .expiresAt(accessExpiry)
                .claim("token_type", "access")
                .claim("roles", user.roles())
                .build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        byte[] random = new byte[48];
        secureRandom.nextBytes(random);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        refreshTokenRepository.save(
                new RefreshToken(hashToken(refreshToken), user.uuid(), now.plus(refreshTokenTtl)));
        return new TokenResponse(accessToken, refreshToken, accessTokenTtl.toSeconds());
    }

    private static String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
