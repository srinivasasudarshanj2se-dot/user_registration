package org.example.user_registration.auth;

import org.example.user_registration.common.EmailAlreadyExistsException;
import org.example.user_registration.common.InvalidRefreshTokenException;
import org.example.user_registration.user.Role;
import org.example.user_registration.user.RoleRepository;
import org.example.user_registration.user.User;
import org.example.user_registration.user.UserRepository;
import org.example.user_registration.auth.AuthDtos.LoginRequest;
import org.example.user_registration.auth.AuthDtos.RefreshRequest;
import org.example.user_registration.auth.AuthDtos.RegisterRequest;
import org.example.user_registration.auth.AuthDtos.TokenResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
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
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository, RoleRepository roleRepository,
                       RefreshTokenRepository refreshTokenRepository, PasswordEncoder passwordEncoder,
                       JwtEncoder jwtEncoder, @Value("${security.jwt.issuer}") String issuer,
                       @Value("${security.jwt.access-token-ttl}") Duration accessTokenTtl,
                       @Value("${security.jwt.refresh-token-ttl}") Duration refreshTokenTtl) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    @Transactional
    public void register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyExistsException(email);
        }
        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new IllegalStateException("Required ROLE_USER seed data is missing"));
        userRepository.save(new User(request.firstName().trim(), request.lastName().trim(), email,
                request.mobile().trim(), passwordEncoder.encode(request.password()), userRole));
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .filter(User::getEnabled)
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPassword()))
                .orElseThrow(() -> new BadCredentialsException("Email or password is incorrect"));
        return issueTokens(user);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        String suppliedHash = hashToken(request.refreshToken());
        RefreshToken existing = refreshTokenRepository.findByTokenHash(suppliedHash)
                .orElseThrow(InvalidRefreshTokenException::new);
        refreshTokenRepository.delete(existing);
        if (!existing.getExpiryDate().isAfter(Instant.now()) || !existing.getUser().getEnabled()) {
            throw new InvalidRefreshTokenException();
        }
        return issueTokens(existing.getUser());
    }

    private TokenResponse issueTokens(User user) {
        Instant now = Instant.now();
        Instant accessExpiry = now.plus(accessTokenTtl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.getEmail())
                .issuedAt(now)
                .expiresAt(accessExpiry)
                .claim("token_type", "access")
                .claim("roles", user.getRoles().stream().map(Role::getName).toList())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        byte[] random = new byte[48];
        secureRandom.nextBytes(random);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        refreshTokenRepository.save(new RefreshToken(hashToken(refreshToken), user, now.plus(refreshTokenTtl)));
        return new TokenResponse(accessToken, refreshToken, accessTokenTtl.toSeconds());
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(java.util.Locale.ROOT);
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
