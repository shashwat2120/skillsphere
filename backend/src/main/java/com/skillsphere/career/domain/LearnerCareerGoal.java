package com.skillsphere.career.domain;

import com.skillsphere.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A learner's declared target role.
 *
 * <p>{@code userId} is a plain value for the same reason it is everywhere else
 * in this codebase outside identity: a mapped association here would place a
 * join across what becomes a service boundary in Sprint 6.
 */
@Entity
@Table(name = "learner_career_goals")
@Getter
@Setter
@NoArgsConstructor
public class LearnerCareerGoal extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "career_role_id", nullable = false)
    private Long careerRoleId;

    @Column(name = "is_primary", nullable = false)
    private boolean primary = true;

    @Column(name = "target_date")
    private LocalDate targetDate;

    /** Declared study capacity, used to pace a generated path. */
    @Column(name = "weekly_minutes", nullable = false)
    private int weeklyMinutes = 300;

    @Column(name = "set_at", nullable = false)
    private Instant setAt = Instant.now();

    @Column(name = "achieved_at")
    private Instant achievedAt;

    public LearnerCareerGoal(Long userId, Long careerRoleId) {
        this.userId = userId;
        this.careerRoleId = careerRoleId;
    }
}
