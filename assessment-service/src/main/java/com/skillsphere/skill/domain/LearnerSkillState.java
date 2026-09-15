package com.skillsphere.skill.domain;

import com.skillsphere.shared.domain.VersionedEntity;
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
 * What the system currently believes about one learner and one skill.
 *
 * <p>The live model of a person. Every answered item updates a row here, and
 * every routing decision reads one — it is the busiest table in the product.
 *
 * <p><b>The user is an id, not an association.</b> There is no
 * {@code @ManyToOne User} on this class, and that is the rule the entire Sprint 6
 * split rests on. A mapped association would place a join across a future service
 * boundary, and once the skill engine and identity live in separate processes
 * that join cannot exist. Referencing by value costs nothing today and is the
 * difference between an extraction and a rewrite later. The database still has a
 * foreign key, which is correct while both tables share one schema.
 *
 * <p><b>Three estimates, kept side by side, because they answer different
 * questions.</b>
 *
 * <ul>
 *   <li>{@code abilityTheta} with {@code abilitySe} — the Item Response Theory
 *       view. Theta is ability on a logit scale and the standard error says how
 *       much to trust it. The diagnostic stops when the error is small enough,
 *       which is what makes it adaptive rather than a fixed-length quiz.</li>
 *   <li>{@code masteryProbability} — Bayesian Knowledge Tracing. Not a score but
 *       a probability that the learner has actually learned the skill, which is
 *       the honest thing to put on a passport and the thing a reviewer asking
 *       "how is 91% computed?" needs a real answer for.</li>
 *   <li>{@code eloRating} — cheap, robust, and useful immediately. IRT needs
 *       calibrated items before it means much; Elo produces something sensible
 *       from the very first response, which is what carries the platform through
 *       its cold start.</li>
 * </ul>
 *
 * <p>Keeping all three is deliberate. Collapsing to one number would leave no way
 * to show the working when the figure is challenged.
 */
@Entity
@Table(name = "learner_skill_state")
@Getter
@Setter
@NoArgsConstructor
public class LearnerSkillState extends VersionedEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    /** IRT ability on the logit scale, in practice roughly -3.0 to +3.0. */
    @Column(name = "ability_theta", nullable = false, precision = 6, scale = 4)
    private BigDecimal abilityTheta = BigDecimal.ZERO;

    /**
     * Standard error of the ability estimate.
     *
     * <p>Starts high and shrinks as evidence accumulates. This is what lets a
     * diagnostic stop early for a learner whose level becomes obvious quickly,
     * while continuing to probe one whose responses are inconsistent.
     */
    @Column(name = "ability_se", nullable = false, precision = 6, scale = 4)
    private BigDecimal abilitySe = BigDecimal.ONE;

    /** Bayesian Knowledge Tracing P(mastered), 0 to 1. */
    @Column(name = "mastery_probability", nullable = false, precision = 5, scale = 4)
    private BigDecimal masteryProbability = new BigDecimal("0.0500");

    @Column(name = "elo_rating", nullable = false)
    private int eloRating = 1200;

    @Column(name = "attempts_count", nullable = false)
    private int attemptsCount = 0;

    @Column(name = "correct_count", nullable = false)
    private int correctCount = 0;

    /**
     * Highest mastery ever reached.
     *
     * <p>Kept so decay can be shown against a high-water mark rather than silently
     * rewriting history. A learner who sees "you were at 88%, now 71%" understands
     * why a refresher is being suggested; one who only ever sees the current number
     * experiences it as the platform arbitrarily lowering their score.
     */
    @Column(name = "peak_mastery", nullable = false, precision = 5, scale = 4)
    private BigDecimal peakMastery = BigDecimal.ZERO;

    @Column(name = "first_seen_at")
    private Instant firstSeenAt;

    @Column(name = "last_practiced_at")
    private Instant lastPracticedAt;

    @Column(name = "mastered_at")
    private Instant masteredAt;

    public LearnerSkillState(Long userId, Skill skill) {
        this.userId = userId;
        this.skill = skill;
        this.firstSeenAt = Instant.now();
    }

    public boolean isMastered(BigDecimal threshold) {
        return masteryProbability.compareTo(threshold) >= 0;
    }

    /**
     * Records that mastery was reached, once.
     *
     * <p>{@code masteredAt} is set only the first time. It marks when the learner
     * demonstrated the skill, and an achievement that silently re-dates itself on
     * every later practice session is not a record of anything.
     */
    public void markMasteredIfNeeded(BigDecimal threshold) {
        if (masteredAt == null && isMastered(threshold)) {
            masteredAt = Instant.now();
        }
    }

    public void recordPractice(boolean correct) {
        attemptsCount++;
        if (correct) {
            correctCount++;
        }
        lastPracticedAt = Instant.now();
    }

    public void updateMastery(BigDecimal newMastery) {
        this.masteryProbability = newMastery;
        if (newMastery.compareTo(peakMastery) > 0) {
            this.peakMastery = newMastery;
        }
    }
}
