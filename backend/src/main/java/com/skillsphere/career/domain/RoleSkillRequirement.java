package com.skillsphere.career.domain;

import com.skillsphere.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One entry in a role's target skill vector.
 *
 * <p>{@code skillId} is a plain value, not a {@code @ManyToOne} to
 * {@code skill.domain.Skill} — the same rule {@code LearnerSkillState} follows
 * for the learner, applied here to the skill side. Career depends on skill only
 * through {@link com.skillsphere.skill.SkillLookup}; a mapped association would
 * reach past that boundary and the build's module test would reject it.
 */
@Entity
@Table(name = "role_skill_requirements")
@Getter
@Setter
@NoArgsConstructor
public class RoleSkillRequirement extends BaseEntity {

    @Column(name = "career_role_id", nullable = false)
    private Long careerRoleId;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    /** Mastery a candidate needs to be considered ready for this skill. */
    @Column(name = "required_mastery", nullable = false, precision = 3, scale = 2)
    private BigDecimal requiredMastery = new BigDecimal("0.70");

    /** How much this skill counts toward overall readiness. */
    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal weight = BigDecimal.ONE;

    /** Core skills gate readiness; non-core skills raise the score but never block it. */
    @Column(name = "is_core", nullable = false)
    private boolean core = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public RoleSkillRequirement(Long careerRoleId, Long skillId, BigDecimal requiredMastery,
                                 BigDecimal weight, boolean core) {
        this.careerRoleId = careerRoleId;
        this.skillId = skillId;
        this.requiredMastery = requiredMastery;
        this.weight = weight;
        this.core = core;
    }
}
