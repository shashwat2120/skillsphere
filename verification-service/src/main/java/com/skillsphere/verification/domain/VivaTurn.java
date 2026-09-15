package com.skillsphere.verification.domain;

import com.skillsphere.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One question, one answer, one evaluation.
 *
 * <p>{@code question} is generated from the learner's own submission and
 * cannot exist before it is made — that ordering is what makes the defence
 * impossible to outsource, not any detection mechanism. {@code evaluation}
 * is a raw JSON string, the LLM judge's structured output verbatim: shape
 * {@code {reasoning, criteriaMet[], confidence, flags[]}}.
 */
@Entity
@Table(name = "viva_turns")
@Getter
@Setter
@NoArgsConstructor
public class VivaTurn extends BaseEntity {

    @Column(name = "viva_session_id", nullable = false)
    private Long vivaSessionId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, columnDefinition = "text")
    private String question;

    @Column(name = "question_anchor", length = 300)
    private String questionAnchor;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_intent", length = 50)
    private QuestionIntent questionIntent;

    @Column(name = "skill_id")
    private Long skillId;

    @Column(columnDefinition = "text")
    private String answer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String evaluation;

    @Column(precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "time_limit_sec", nullable = false)
    private int timeLimitSec = 180;

    @Column(name = "asked_at", nullable = false)
    private Instant askedAt = Instant.now();

    @Column(name = "answered_at")
    private Instant answeredAt;

    public VivaTurn(Long vivaSessionId, int position, String question, String questionAnchor,
                     QuestionIntent questionIntent, Long skillId, int timeLimitSec) {
        this.vivaSessionId = vivaSessionId;
        this.position = position;
        this.question = question;
        this.questionAnchor = questionAnchor;
        this.questionIntent = questionIntent;
        this.skillId = skillId;
        this.timeLimitSec = timeLimitSec;
    }

    public boolean isAnswered() {
        return answeredAt != null;
    }

    public boolean isOverdue() {
        return Instant.now().isAfter(askedAt.plusSeconds(timeLimitSec));
    }

    public void recordAnswer(String answer, String evaluation, BigDecimal score) {
        this.answer = answer;
        this.evaluation = evaluation;
        this.score = score;
        this.answeredAt = Instant.now();
    }
}
