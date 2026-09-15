package com.skillsphere.verification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * How much completing a project counts toward one skill.
 *
 * <p>{@code skillId} lives inside the composite key rather than behind a
 * {@code @ManyToOne} for the same boundary reason as everywhere else this
 * module touches a skill: verification depends on {@code skill} only through
 * {@link com.skillsphere.skill.SkillLookup}.
 */
@Entity
@Table(name = "project_skills")
@Getter
@Setter
@NoArgsConstructor
public class ProjectSkill {

    @EmbeddedId
    private ProjectSkillId id;

    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal weight = BigDecimal.ONE;

    public ProjectSkill(Long projectId, Long skillId, BigDecimal weight) {
        this.id = new ProjectSkillId(projectId, skillId);
        this.weight = weight;
    }
}
