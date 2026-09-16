package com.skillsphere.identity.domain;

import com.skillsphere.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One single-use MFA recovery code.
 *
 * <p>Ten of these are minted at TOTP enrollment and shown to the user exactly
 * once — only {@link #codeHash} is ever persisted, following the same rule as
 * every other credential in this schema. Each is a fallback second factor for
 * someone who has lost their authenticator app; consuming one requires the
 * password to already have been verified, since a recovery code alone is not
 * a first factor.
 */
@Entity
@Table(name = "mfa_recovery_codes")
@Getter
@Setter
@NoArgsConstructor
public class MfaRecoveryCode extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public MfaRecoveryCode(Long userId, String codeHash) {
        this.userId = userId;
        this.codeHash = codeHash;
        this.createdAt = Instant.now();
    }

    public boolean isUsable() {
        return usedAt == null;
    }

    public void consume() {
        this.usedAt = Instant.now();
    }
}
