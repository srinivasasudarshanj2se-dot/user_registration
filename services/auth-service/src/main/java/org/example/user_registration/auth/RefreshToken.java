package org.example.user_registration.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "user_uuid", nullable = false, length = 50)
    private String userUuid;

    @Column(name = "expiry_date", nullable = false)
    private Instant expiryDate;

    protected RefreshToken() {
    }

    public RefreshToken(String tokenHash, String userUuid, Instant expiryDate) {
        this.tokenHash = tokenHash;
        this.userUuid = userUuid;
        this.expiryDate = expiryDate;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getUserUuid() {
        return userUuid;
    }

    public Instant getExpiryDate() {
        return expiryDate;
    }
}
