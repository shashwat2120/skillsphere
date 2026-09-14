package com.skillsphere.analytics.domain;

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

import java.time.Instant;

/**
 * One row per meaningful learner action — the event-sourced spine.
 *
 * <p>Append-only, never edited. Everything downstream — daily rollups, risk
 * scoring — reads this table rather than the modules that originally
 * produced the actions, which is what lets analytics tolerate lag without
 * ever touching assessment's or skill's own tables. In Sprint 6 the same
 * rows arrive over Kafka instead of an in-process event listener; nothing
 * downstream of this table changes.
 */
@Entity
@Table(name = "learning_events")
@Getter
@Setter
@NoArgsConstructor
public class LearningEvent extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private LearningEventType eventType;

    @Column(name = "entity_type", length = 30)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "skill_id")
    private Long skillId;

    /**
     * Whether this action was correct, for event types where that applies —
     * queried constantly by rollups and risk scoring, so it earns a real
     * column rather than living only inside {@link #payload}.
     */
    @Column
    private Boolean correct;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload = "{}";

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    public LearningEvent(Long userId, LearningEventType eventType, String entityType, Long entityId,
                          Long skillId, Boolean correct, String payload, Instant occurredAt) {
        this.userId = userId;
        this.eventType = eventType;
        this.entityType = entityType;
        this.entityId = entityId;
        this.skillId = skillId;
        this.correct = correct;
        this.payload = payload;
        this.occurredAt = occurredAt;
    }
}
