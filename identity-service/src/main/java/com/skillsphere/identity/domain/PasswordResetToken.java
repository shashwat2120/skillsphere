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
 * A single-use token authorising a password change.
 *
 * <p>This is the most dangerous token the system issues. An email verification
 * link proves an address; this one <em>replaces a credential</em>, so anyone
 * holding it can take the account. Three properties contain that, and all three
 * are required together.
 *
 * <p><b>Short-lived.</b> Thirty minutes rather than the twenty-four hours given
 * to email verification. A reset link sitting unused in an inbox is a standing
 * key to the account, and inboxes are read by more people, on more devices, than
 * anyone assumes.
 *
 * <p><b>Single use.</b> Consumed on success, so a link forwarded, logged by a
 * mail scanner, or recovered from browser history cannot be replayed.
 *
 * <p><b>Stored only as a hash.</b> A database leak yields no working links —
 * which matters more here than anywhere else, because a leak that exposed live
 * reset tokens would be a leak of every account at once.
 */
@Entity
@Table(name = "password_reset_tokens")
@Getter
@Setter
@NoArgsConstructor
public class PasswordResetToken extends BaseEntity {

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
