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
 * A refresh token, stored only as a hash.
 *
 * <p><b>Why these are not JWTs.</b> An access token is deliberately stateless
 * and short-lived. A refresh token is the opposite: it lives for weeks and must
 * be revocable with certainty, so it is an opaque random string recorded here.
 * A self-contained signed refresh token could not be revoked at all — it would
 * stay valid for its full lifetime no matter what.
 *
 * <p><b>Only the hash is stored.</b> The plaintext exists once, in the response
 * to the client. A database dump therefore yields nothing usable, exactly as
 * with passwords. Treating a long-lived credential as anything less would be
 * inconsistent.
 *
 * <p><b>Rotation and reuse detection.</b> Every refresh issues a new token and
 * marks the old one {@code rotated}, recording the successor in
 * {@code replacedBy}. That forms a chain, and the chain is what makes theft
 * detectable: a legitimate client never presents a rotated token twice, so if a
 * rotated token is presented again it means the token was copied. At that point
 * it is unknowable whether the attacker or the real user holds the current
 * token, so the entire chain is revoked and both are forced to log in again.
 * Annoying the real user is the correct trade against leaving a thief with a
 * live session.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private UserDevice device;

    /** SHA-256 of the plaintext token. Uniquely indexed, so a lookup is a single hit. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason", length = 50)
    private String revokedReason;

    /** Successor in the rotation chain — the field reuse detection walks. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaced_by_id")
    private RefreshToken replacedBy;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Usable means live and never rotated. Note that a <em>rotated</em> token is
     * revoked, so presenting one is not merely invalid — it is the signal that
     * something copied it.
     */
    public boolean isUsable() {
        return !isRevoked() && !isExpired();
    }

    public void revoke(RevocationReason reason) {
        if (revokedAt == null) {
            this.revokedAt = Instant.now();
            this.revokedReason = reason.name();
        }
    }

    public void rotateTo(RefreshToken successor) {
        revoke(RevocationReason.ROTATED);
        this.replacedBy = successor;
    }

    public enum RevocationReason {
        /** Normal refresh — superseded by a successor. */
        ROTATED,
        /** The user logged out. */
        LOGOUT,
        /** An already-rotated token was presented again: the chain is compromised. */
        REUSE_DETECTED,
        /** Password changed, so every existing session is invalidated. */
        PASSWORD_CHANGED,
        /** An admin terminated the session, or the account was suspended. */
        ADMIN_REVOKED
    }
}
