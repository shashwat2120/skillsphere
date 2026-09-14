package com.skillsphere.verification.domain;

import com.skillsphere.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * An immutable record of a demonstrated skill — never updated after creation.
 *
 * <p>The skill passport (Phase 7) is computed from these rows and nothing
 * else, in particular never from a stored number a learner could influence.
 * {@code uq_ev_source} ({@code source_type, source_id, skill_id}) is what
 * keeps this table append-only in practice: the same viva session can never
 * mint two evidence rows for the same skill even if something calls this
 * twice.
 */
@Entity
@Table(name = "evidence")
@Getter
@Setter
@NoArgsConstructor
public class Evidence extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, length = 20)
    private EvidenceType evidenceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private EvidenceSourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal weight = BigDecimal.ONE;

    @Column(precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "verified_by")
    private Long verifiedBy;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt = Instant.now();

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Evidence(Long userId, Long skillId, EvidenceType evidenceType, EvidenceSourceType sourceType,
                     Long sourceId, BigDecimal weight, BigDecimal score) {
        this.userId = userId;
        this.skillId = skillId;
        this.evidenceType = evidenceType;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.weight = weight;
        this.score = score;
    }
}
