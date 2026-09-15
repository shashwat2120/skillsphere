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
 * The scalable oral defence: a fixed-size sequence of questions generated
 * from the learner's own submission, asked one at a time.
 *
 * <p>{@code generatorModel} is recorded on every session for reproducibility
 * — if a verdict is ever disputed, this is what model produced the questions
 * that were asked, not whatever version happens to be configured today.
 */
@Entity
@Table(name = "viva_sessions")
@Getter
@Setter
@NoArgsConstructor
public class VivaSession extends BaseEntity {

    @Column(name = "submission_id", nullable = false)
    private Long submissionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VivaStatus status = VivaStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private VivaVerdict verdict;

    @Column(name = "overall_score", precision = 5, scale = 2)
    private BigDecimal overallScore;

    @Column(name = "turns_planned", nullable = false)
    private int turnsPlanned = 5;

    @Column(name = "turns_completed", nullable = false)
    private int turnsCompleted = 0;

    @Column(name = "generator_model", length = 100)
    private String generatorModel;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public VivaSession(Long submissionId, int turnsPlanned, String generatorModel) {
        this.submissionId = submissionId;
        this.turnsPlanned = turnsPlanned;
        this.generatorModel = generatorModel;
        this.status = VivaStatus.IN_PROGRESS;
        this.startedAt = Instant.now();
    }

    public void recordTurnCompleted() {
        turnsCompleted++;
    }

    public void conclude(VivaVerdict verdict, BigDecimal overallScore) {
        this.verdict = verdict;
        this.overallScore = overallScore;
        this.status = VivaStatus.COMPLETED;
        this.completedAt = Instant.now();
    }
}
