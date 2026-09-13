package com.skillsphere.skill.domain;

import com.skillsphere.shared.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A node in the skill graph — the first-class entity of the whole platform.
 *
 * <p>Everything else exists in service of this. A course is evidence that a
 * skill was acquired, an item is an instrument for measuring one, a career is a
 * target vector of them. That inversion is the single decision that separates
 * SkillSphere from a course catalogue with extra screens.
 *
 * <p><b>What this entity deliberately does not hold.</b> No learner state, no
 * mastery, no progress. Those live on {@link LearnerSkillState}, keyed by skill
 * id and user id. Hanging a collection of learner states off a skill would mean
 * loading a row per learner to answer a question about the skill itself, and
 * popular skills would become unloadable.
 *
 * <p>It also holds no reference to its prerequisites as a mapped collection.
 * Traversal is done with recursive SQL rather than by walking object graphs,
 * because the question that matters — "what is this learner ready for?" — is a
 * transitive one, and answering it through JPA associations means one query per
 * node at every level of depth.
 */
@Entity
@Table(name = "skills")
@Getter
@Setter
@NoArgsConstructor
public class Skill extends AuditableEntity {

    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private SkillCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "level_band", nullable = false, length = 20)
    private LevelBand levelBand = LevelBand.INTERMEDIATE;

    /**
     * Rough study time, used to pace a generated path and to power the
     * what-if simulator. An estimate, never a measurement — real time-on-task
     * is recorded by the analytics module.
     */
    @Column(name = "est_minutes", nullable = false)
    private int estMinutes = 60;

    /**
     * How fast unused mastery erodes, per day.
     *
     * <p>Per-skill rather than global because skills genuinely differ: the shape
     * of a {@code for} loop is not forgotten, while the exact flags of a CLI tool
     * are. Setting this to zero marks a skill as durable and exempts it from
     * decay entirely.
     */
    @Column(name = "decay_rate", nullable = false, precision = 6, scale = 5)
    private BigDecimal decayRate = new BigDecimal("0.00200");

    /**
     * Retired skills are deactivated rather than deleted.
     *
     * <p>Evidence rows, mastery history and completed path steps all reference
     * this skill, and a learner's passport must keep meaning something after the
     * catalogue moves on. Deleting would either cascade away someone's proof of
     * competence or leave dangling references.
     */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public Skill(String slug, String name) {
        this.slug = slug;
        this.name = name;
    }
}
