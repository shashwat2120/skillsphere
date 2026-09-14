package com.skillsphere.career.domain;

import com.skillsphere.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One generated route toward a career goal.
 *
 * <p>Re-planning never mutates a path — it supersedes it with a new version, so
 * a learner's route history stays intact and auditable rather than silently
 * rewritten. {@link #version} plus the unique constraint on
 * {@code (user_id, career_role_id)} where {@code status = 'ACTIVE'} is what
 * keeps that true at the database, not just in application code that could
 * drift.
 */
@Entity
@Table(name = "learning_paths")
@Getter
@Setter
@NoArgsConstructor
public class LearningPath extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "career_role_id", nullable = false)
    private Long careerRoleId;

    @Column(nullable = false)
    private int version = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PathStatus status = PathStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "generation_reason", nullable = false, length = 40)
    private GenerationReason generationReason = GenerationReason.INITIAL;

    @Column(name = "total_steps", nullable = false)
    private int totalSteps = 0;

    @Column(name = "completed_steps", nullable = false)
    private int completedSteps = 0;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt = Instant.now();

    @Column(name = "superseded_at")
    private Instant supersededAt;

    public LearningPath(Long userId, Long careerRoleId, int version, GenerationReason reason) {
        this.userId = userId;
        this.careerRoleId = careerRoleId;
        this.version = version;
        this.generationReason = reason;
    }

    public void supersede() {
        this.status = PathStatus.SUPERSEDED;
        this.supersededAt = Instant.now();
    }
}
