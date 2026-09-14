package com.skillsphere.realtime.domain;

import com.skillsphere.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One participant's answer to one arena question.
 *
 * <p>The database's {@code UNIQUE (arena_question_id, participant_id)} is
 * the real guarantee behind "one answer per question" — a flaky mobile
 * connection retrying a submit must not double-score, and enforcing that at
 * the database is the only version of this guarantee that survives two
 * requests racing each other.
 */
@Entity
@Table(name = "arena_answers")
@Getter
@Setter
@NoArgsConstructor
public class ArenaAnswer extends BaseEntity {

    @Column(name = "arena_question_id", nullable = false)
    private Long arenaQuestionId;

    @Column(name = "participant_id", nullable = false)
    private Long participantId;

    @Column(name = "selected_option_id")
    private Long selectedOptionId;

    @Column(name = "is_correct", nullable = false)
    private boolean correct = false;

    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    @Column(name = "points_awarded", nullable = false)
    private int pointsAwarded = 0;

    @Column(name = "answered_at", nullable = false)
    private Instant answeredAt = Instant.now();

    public ArenaAnswer(Long arenaQuestionId, Long participantId, Long selectedOptionId,
                        boolean correct, Integer responseTimeMs, int pointsAwarded) {
        this.arenaQuestionId = arenaQuestionId;
        this.participantId = participantId;
        this.selectedOptionId = selectedOptionId;
        this.correct = correct;
        this.responseTimeMs = responseTimeMs;
        this.pointsAwarded = pointsAwarded;
    }
}
