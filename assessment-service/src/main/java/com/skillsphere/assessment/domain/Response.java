package com.skillsphere.assessment.domain;

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
 * One answer, recorded permanently.
 *
 * <p>Append-only: never updated, never deleted. This table is the evidence
 * behind every claim the platform makes about anybody, and the moment a row here
 * could be edited, nothing derived from it could be trusted.
 *
 * <p>It stores the ability estimate on <em>both sides</em> of the response, which
 * is unusual and deliberate. Keeping only the outcome would leave the engine a
 * black box after the fact: you could see that someone answered correctly, but
 * not why that moved their mastery six points rather than one. With before and
 * after recorded, any figure on a passport can be replayed and checked — which
 * is what "proof that can be checked" has to mean once it stops being a slogan.
 */
@Entity
@Table(name = "responses")
@Getter
@Setter
@NoArgsConstructor
public class Response extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "assessment_id")
    private Long assessmentId;

    @Column(name = "selected_option_id")
    private Long selectedOptionId;

    @Column(name = "free_text_answer", columnDefinition = "text")
    private String freeTextAnswer;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    /**
     * How long the learner took.
     *
     * <p>Diagnostic in its own right: a correct answer in two seconds on a hard
     * item suggests recognition rather than reasoning, and a long pause on an easy
     * one suggests the phrasing is the obstacle rather than the concept.
     */
    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    @Column(name = "ability_before", precision = 6, scale = 4)
    private BigDecimal abilityBefore;

    @Column(name = "ability_after", precision = 6, scale = 4)
    private BigDecimal abilityAfter;

    @Column(name = "mastery_before", precision = 5, scale = 4)
    private BigDecimal masteryBefore;

    @Column(name = "mastery_after", precision = 5, scale = 4)
    private BigDecimal masteryAfter;

    /**
     * The belief inferred from the distractor chosen.
     *
     * <p>Null on a correct answer, and null on a wrong answer whose option was
     * never tagged. The second case is a gap in the item rather than in the
     * learner: the platform saw a mistake and learned nothing from it.
     */
    @Column(name = "misconception_id")
    private Long misconceptionId;

    @Column(name = "answered_at", nullable = false, updatable = false)
    private Instant answeredAt = Instant.now();
}
