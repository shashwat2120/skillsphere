package com.skillsphere.career.domain;

import com.skillsphere.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A frozen, shareable view of a learner's verified skills.
 *
 * <p>Frozen on purpose: an employer who opens a share link a week from now
 * should see the passport as it stood when it was shared, not a live number
 * that could have moved — and could even show <em>less</em> than what was
 * claimed if a skill later decays. {@code snapshot} carries the full rendered
 * view (skills, scores, evidence references) as JSON, so the public endpoint
 * that serves it needs no authentication and no access to any other table —
 * it can be exactly as public as the token that unlocks it.
 *
 * <p>The live, non-frozen view a signed-in learner sees on their own
 * "Passport" page is not this table — it's computed on request by
 * {@code PassportService} from current data. A snapshot exists only once
 * something is deliberately shared.
 */
@Entity
@Table(name = "passport_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class PassportSnapshot extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "career_role_id")
    private Long careerRoleId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String snapshot;

    @Column(name = "readiness_score", precision = 5, scale = 2)
    private BigDecimal readinessScore;

    @Column(name = "evidence_count", nullable = false)
    private int evidenceCount = 0;

    @Column(name = "share_token", unique = true, length = 64)
    private String shareToken;

    @Column(name = "share_expires_at")
    private Instant shareExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public PassportSnapshot(Long userId, Long careerRoleId, String snapshot,
                             BigDecimal readinessScore, int evidenceCount) {
        this.userId = userId;
        this.careerRoleId = careerRoleId;
        this.snapshot = snapshot;
        this.readinessScore = readinessScore;
        this.evidenceCount = evidenceCount;
    }

    public boolean isShared() {
        return shareToken != null;
    }

    public boolean isExpired() {
        return shareExpiresAt != null && Instant.now().isAfter(shareExpiresAt);
    }
}
