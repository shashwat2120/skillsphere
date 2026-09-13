package com.skillsphere.skill.domain;

import com.skillsphere.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A directed edge: {@code skill} requires {@code prerequisiteSkill}.
 *
 * <p>Read it as "you should learn the prerequisite first". The edges together
 * form a directed acyclic graph, and acyclicity is the property everything else
 * depends on — a cycle means a learner is told to master A before B and B before
 * A, path generation never terminates, and the frontier query returns nothing at
 * all because no skill's prerequisites can ever be satisfied.
 *
 * <p><b>Acyclicity cannot be a database constraint.</b> PostgreSQL can reject a
 * self-loop with a CHECK, which is done here, but a longer cycle is a property of
 * the whole edge set rather than of any single row, and SQL has no constraint
 * that expresses it. So the check runs in the application before every insert:
 * an edge is refused if the prerequisite already depends on the skill, directly
 * or transitively. This is the one invariant where the database cannot be the
 * final guard, so the service layer has to be.
 */
@Entity
@Table(name = "skill_prerequisites")
@Getter
@Setter
@NoArgsConstructor
public class SkillPrerequisite extends BaseEntity {

    /** The skill that has a prerequisite. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    /** The skill that must come first. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prerequisite_skill_id", nullable = false)
    private Skill prerequisiteSkill;

    /**
     * How binding this edge is, from just above 0 to 1.
     *
     * <p>1.00 is a hard gate: the path engine will not offer the skill until the
     * prerequisite is mastered. Lower values mean "helpful but not blocking" —
     * useful for the common case where prior knowledge makes something easier
     * without being genuinely required.
     *
     * <p>Modelling that difference matters. A graph built entirely from hard
     * gates produces a single rigid ordering and destroys the point of
     * personalisation: every learner walks the same line, which is the behaviour
     * of the sequential unlocking this product exists to replace.
     */
    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal strength = BigDecimal.ONE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public SkillPrerequisite(Skill skill, Skill prerequisiteSkill, BigDecimal strength) {
        this.skill = skill;
        this.prerequisiteSkill = prerequisiteSkill;
        this.strength = strength;
        this.createdAt = Instant.now();
    }

    /** Whether this edge blocks progression outright, as opposed to merely advising. */
    public boolean isHardGate() {
        return strength.compareTo(new BigDecimal("0.99")) >= 0;
    }
}
