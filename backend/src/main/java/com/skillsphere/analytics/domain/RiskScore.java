package com.skillsphere.analytics.domain;

import com.skillsphere.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One computation of one learner's risk of disengaging — never overwritten,
 * so the history of how a learner's risk moved stays intact rather than
 * being silently replaced by the latest run.
 *
 * <p>{@link #factors} is not optional detail. The flag is never presented
 * without its reasons — an instructor acting on "this learner is at risk"
 * needs to know why before they can do anything useful with it, which is
 * the same principle behind every other explainability surface in this
 * product (path-step rationale, misconception feedback, viva evaluation).
 */
@Entity
@Table(name = "risk_scores")
@Getter
@Setter
@NoArgsConstructor
public class RiskScore extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "course_id")
    private Long courseId;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RiskBand band;

    /**
     * Shape: {@code {inactivityDays, recentAccuracy, failureCluster,
     * failureClusterSkill, daysSinceEnrolment, earlyWindow}} — every factor
     * that contributed, in plain enough form for the dashboard to render
     * directly without re-deriving anything.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String factors = "{}";

    @Column(name = "is_early_window", nullable = false)
    private boolean earlyWindow = false;

    @Column(name = "intervened_at")
    private Instant intervenedAt;

    @Column(name = "intervened_by")
    private Long intervenedBy;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt = Instant.now();

    public RiskScore(Long userId, BigDecimal score, RiskBand band, String factors, boolean earlyWindow) {
        this.userId = userId;
        this.score = score;
        this.band = band;
        this.factors = factors;
        this.earlyWindow = earlyWindow;
    }

    public void markIntervened(Long instructorId) {
        this.intervenedAt = Instant.now();
        this.intervenedBy = instructorId;
    }
}
