package com.skillsphere.skill.domain;

import com.skillsphere.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Append-only ledger of every mastery change.
 *
 * <p>{@link LearnerSkillState} holds the current belief; this holds how it got
 * there. The distinction matters because the passport asserts something about a
 * person, and an assertion with no derivation is just a number — when an
 * employer or a reviewer asks what 91% rests on, this table is the answer.
 *
 * <p>Never updated and never deleted. A mutable audit record is not an audit
 * record, and the moment a row here could be edited, nothing derived from it
 * could be trusted.
 *
 * <p>It also feeds the progress chart and the decay curve, both of which need
 * history rather than a current value.
 */
@Entity
@Table(name = "skill_mastery_history")
@Getter
@Setter
@NoArgsConstructor
public class SkillMasteryHistory extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Column(name = "mastery_probability", nullable = false, precision = 5, scale = 4)
    private BigDecimal masteryProbability;

    @Column(name = "ability_theta", nullable = false, precision = 6, scale = 4)
    private BigDecimal abilityTheta;

    /** What caused the change: RESPONSE, ASSESSMENT, PROJECT, VIVA, INSTRUCTOR, DECAY, INITIAL. */
    @Column(name = "trigger_type", nullable = false, length = 30)
    private String triggerType;

    /** Id of the thing that caused it, so a figure can be traced to its evidence. */
    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt = Instant.now();

    public static SkillMasteryHistory of(Long userId, Skill skill, BigDecimal mastery,
                                         BigDecimal theta, String triggerType, Long sourceId) {
        SkillMasteryHistory entry = new SkillMasteryHistory();
        entry.userId = userId;
        entry.skill = skill;
        entry.masteryProbability = mastery;
        entry.abilityTheta = theta;
        entry.triggerType = triggerType;
        entry.sourceId = sourceId;
        entry.recordedAt = Instant.now();
        return entry;
    }
}
