package com.skillsphere.identity.domain;

import com.skillsphere.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A single-use token proving control of an email address.
 *
 * <p>Three properties make this safe, and all three are required together:
 * the token is unguessable, it expires, and it is consumed on use. Dropping any
 * one of them is a known failure mode — a permanent verification link sitting
 * in an inbox is a standing credential, and a reusable one can be replayed by
 * anyone who later reads that mailbox.
 *
 * <p>Only the hash is stored, so a database leak yields no working links.
 */
@Entity
@Table(name = "email_verification_tokens")
@Getter
@Setter
@NoArgsConstructor
public class EmailVerificationToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isUsable() {
        return usedAt == null && Instant.now().isBefore(expiresAt);
    }

    public void consume() {
        this.usedAt = Instant.now();
    }
}
