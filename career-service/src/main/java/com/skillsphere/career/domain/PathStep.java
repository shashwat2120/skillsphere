package com.skillsphere.career.domain;

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
 * One step of a generated learning path, and the explainability feature.
 *
 * <p>{@code rationale} is written once, at generation time, and never
 * reconstructed afterwards — reconstructing "why" later from the learner's
 * current state would produce a plausible-sounding fiction rather than a
 * record. It is stored as a raw JSON string rather than a parsed object graph
 * because this entity has no business interpreting it: {@code PathGenerator}
 * writes it, the web layer reads it back verbatim and hands it to the
 * frontend. Mapped with Hibernate's native JSON support (no extra dependency
 * needed since Hibernate 6) rather than a serialised {@code Map}, keeping the
 * exact JSON shape the generator chose in full control of the writer, not the
 * ORM.
 */
@Entity
@Table(name = "path_steps")
@Getter
@Setter
@NoArgsConstructor
public class PathStep extends BaseEntity {

    @Column(name = "learning_path_id", nullable = false)
    private Long learningPathId;

    @Column(nullable = false)
    private int position;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 20)
    private ActivityType activityType;

    @Column(name = "activity_id")
    private Long activityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StepStatus status = StepStatus.LOCKED;

    @Column(name = "est_minutes", nullable = false)
    private int estMinutes = 30;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String rationale = "{}";

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public PathStep(Long learningPathId, int position, Long skillId, ActivityType activityType,
                     int estMinutes, String rationale) {
        this.learningPathId = learningPathId;
        this.position = position;
        this.skillId = skillId;
        this.activityType = activityType;
        this.estMinutes = estMinutes;
        this.rationale = rationale;
    }

    public void complete() {
        this.status = StepStatus.COMPLETED;
        this.completedAt = Instant.now();
    }
}
