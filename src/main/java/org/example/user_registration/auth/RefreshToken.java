package org.example.user_registration.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.example.user_registration.user.User;

import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expiry_date", nullable = false)
    private Instant expiryDate;

    protected RefreshToken() {
    }

    public RefreshToken(String tokenHash, User user, Instant expiryDate) {
        this.tokenHash = tokenHash;
        this.user = user;
        this.expiryDate = expiryDate;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public User getUser() {
        return user;
    }

    public Instant getExpiryDate() {
        return expiryDate;
    }
}
