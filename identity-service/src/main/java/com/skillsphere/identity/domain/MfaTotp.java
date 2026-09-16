package com.skillsphere.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A user's TOTP enrollment.
 *
 * <p>The primary key is the user id itself, not a surrogate — a user has at
 * most one TOTP factor, matching the {@code mfa_totp} table's own
 * {@code user_id PK}. {@link #secretEncrypted} is never the plaintext RFC
 * 6238 secret; see {@code MfaSecretCipher} for the encryption applied before
 * a row is ever written.
 *
 * <p>{@link #enabled} is deliberately separate from "a row exists". Enrollment
 * writes the row first and only flips this to {@code true} once the caller has
 * proven possession by submitting a valid code — otherwise a half-finished
 * enrollment (secret generated, never confirmed) would already gate login.
 */
@Entity
@Table(name = "mfa_totp")
@Getter
@Setter
@NoArgsConstructor
public class MfaTotp {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "secret_encrypted", nullable = false, length = 500)
    private String secretEncrypted;

    @Column(nullable = false)
    private boolean enabled = false;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public MfaTotp(Long userId, String secretEncrypted) {
        this.userId = userId;
        this.secretEncrypted = secretEncrypted;
        this.createdAt = Instant.now();
    }

    public void confirm() {
        this.enabled = true;
        this.confirmedAt = Instant.now();
    }
}
