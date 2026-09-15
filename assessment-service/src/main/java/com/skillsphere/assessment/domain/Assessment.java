package com.skillsphere.assessment.domain;

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
import java.math.RoundingMode;
import java.time.Instant;

/**
 * One sitting: a sequence of items served adaptively to one learner.
 *
 * <p>Holds the session, not the result. Ability lives on the learner skill state
 * and persists across sittings; this records what happened during a particular
 * one — how many items, why it stopped, how long it took.
 *
 * <p>That separation matters because starting a new assessment does not reset
 * what the platform knows. Evidence accumulates across every sitting a learner
 * has ever done, which is exactly why a second diagnostic is shorter than the
 * first.
 */
@Entity
@Table(name = "assessments")
@Getter
@Setter
@NoArgsConstructor
public class Assessment extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssessmentType type;

    @Column(name = "skill_id")
    private Long skillId;

    @Column(name = "career_role_id")
    private Long careerRoleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssessmentStatus status = AssessmentStatus.IN_PROGRESS;

    @Column(name = "items_served", nullable = false)
    private int itemsServed = 0;

    @Column(name = "items_correct", nullable = false)
    private int itemsCorrect = 0;

    @Column(precision = 5, scale = 2)
    private BigDecimal score;

    @Enumerated(EnumType.STRING)
    @Column(name = "termination_reason", length = 30)
    private TerminationReason terminationReason;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    public Assessment(Long userId, AssessmentType type, Long skillId) {
        this.userId = userId;
        this.type = type;
        this.skillId = skillId;
        this.startedAt = Instant.now();
    }

    public void recordServed() {
        itemsServed++;
    }

    public void recordAnswer(boolean correct) {
        if (correct) {
            itemsCorrect++;
        }
    }

    public void complete(TerminationReason reason) {
        this.status = AssessmentStatus.COMPLETED;
        this.terminationReason = reason;
        this.completedAt = Instant.now();

        // Raw proportion, kept for reference only — nothing routes on it. An
        // adaptive test deliberately steers toward roughly 50% correct whatever
        // the learner's level, so this number says far less than the ability
        // estimate and would actively mislead if read as a grade.
        if (itemsServed > 0) {
            this.score = BigDecimal.valueOf(itemsCorrect * 100.0 / itemsServed)
                    .setScale(2, RoundingMode.HALF_UP);
        }
    }

    public boolean isActive() {
        return status == AssessmentStatus.IN_PROGRESS;
    }
}
