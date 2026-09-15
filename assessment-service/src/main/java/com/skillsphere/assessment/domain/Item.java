package com.skillsphere.assessment.domain;

import com.skillsphere.shared.domain.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A question, and the psychometric parameters that make it measurable.
 *
 * <p>An item here is an <em>instrument</em>, not content. The distinction is what
 * separates this from a quiz: a quiz question is right or wrong, an instrument
 * has known properties and those properties are what let a response say something
 * about the person answering.
 *
 * <p><b>The cold-start problem, and this class's answer to it.</b> Item Response
 * Theory needs calibrated difficulty before it produces meaningful estimates, and
 * calibration needs responses — so a brand-new platform has an engine that cannot
 * work until it has already been used. The usual fix is to pretend the problem
 * does not exist and ship arbitrary numbers.
 *
 * <p>Instead the author states a difficulty and that becomes a Bayesian prior.
 * {@link #declaredDifficulty} is kept permanently separate from
 * {@link #difficultyB} so the guess and the learned value never merge: on day one
 * routing uses the prior, and as responses arrive the learned value takes over
 * and the two can be compared. An author whose declared difficulties diverge
 * sharply from reality is itself a finding worth surfacing.
 *
 * <p><b>Why three parameters rather than one.</b> {@code difficultyB} says where
 * on the ability scale an item bites. {@code discriminationA} says how sharply it
 * separates learners near that point — a low value means the item is noise and
 * strong and weak learners do about equally well on it, which is the signature of
 * a badly written question. {@code eloRating} is a cheap parallel estimate that
 * is usable from the first response, covering the period before IRT has enough
 * data to be trusted.
 */
@Entity
@Table(name = "items")
@Getter
@Setter
@NoArgsConstructor
public class Item extends AuditableEntity {

    /** The skill this item measures. Referenced by id — no cross-module association. */
    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(nullable = false, columnDefinition = "text")
    private String stem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ItemType type = ItemType.MCQ;

    /**
     * Shown after answering, right or wrong.
     *
     * <p>Withholding it from correct answers is a mistake: a learner who guessed
     * correctly gets no correction, and the platform records mastery it has not
     * actually established.
     */
    @Column(columnDefinition = "text")
    private String explanation;

    // ---- Psychometrics ------------------------------------------------------

    /**
     * The author's estimate, on a -3 to +3 scale. Never overwritten by learning —
     * it is the prior, and keeping it lets the guess be compared to reality.
     */
    @Column(name = "declared_difficulty", nullable = false, precision = 4, scale = 2)
    private BigDecimal declaredDifficulty = BigDecimal.ZERO;

    /** IRT difficulty, learned from responses. Same scale as learner ability. */
    @Column(name = "difficulty_b", nullable = false, precision = 6, scale = 4)
    private BigDecimal difficultyB = BigDecimal.ZERO;

    /**
     * IRT discrimination. Higher means the item separates learners more sharply
     * around its difficulty; a value near zero means it tells us almost nothing
     * and the item should be reviewed rather than asked.
     */
    @Column(name = "discrimination_a", nullable = false, precision = 6, scale = 4)
    private BigDecimal discriminationA = BigDecimal.ONE;

    /** Cheap, robust, usable from the very first response. */
    @Column(name = "elo_rating", nullable = false)
    private int eloRating = 1200;

    /**
     * True once enough responses exist for the learned parameters to be trusted
     * over the declared prior. Explicit rather than inferred from a count so the
     * threshold can change without silently reinterpreting historical items.
     */
    @Column(name = "is_calibrated", nullable = false)
    private boolean calibrated = false;

    @Column(name = "times_seen", nullable = false)
    private int timesSeen = 0;

    @Column(name = "times_correct", nullable = false)
    private int timesCorrect = 0;

    @Column(name = "avg_response_ms")
    private Integer avgResponseMs;

    @Column(name = "author_id")
    private Long authorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ItemStatus status = ItemStatus.DRAFT;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Item(Long skillId, String stem, Long authorId) {
        this.skillId = skillId;
        this.stem = stem;
        this.authorId = authorId;
    }

    /**
     * The difficulty the engine should use right now.
     *
     * <p>Falls back to the author's prior until calibration, which is what makes
     * the bank usable on day one instead of requiring a corpus of responses that
     * cannot exist yet.
     */
    public BigDecimal effectiveDifficulty() {
        return calibrated ? difficultyB : declaredDifficulty;
    }

    /**
     * Proportion answered correctly — classical test theory's p-value.
     *
     * <p>A value near 1.0 or 0.0 means the item carries almost no information:
     * everyone gets it right, or nobody does, and either way it fails to
     * distinguish between learners.
     */
    public BigDecimal pValue() {
        if (timesSeen == 0) {
            return null;
        }
        return BigDecimal.valueOf(timesCorrect)
                .divide(BigDecimal.valueOf(timesSeen), 4, java.math.RoundingMode.HALF_UP);
    }

    public void recordResponse(boolean correct) {
        timesSeen++;
        if (correct) {
            timesCorrect++;
        }
    }
}
