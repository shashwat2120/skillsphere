package com.skillsphere.verification.domain;

import com.skillsphere.shared.domain.AuditableEntity;
import com.skillsphere.shared.domain.LevelBand;
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
 * A real project with a rubric — the artefact a viva defends.
 *
 * <p>{@code instructorId} is a plain value for the same reason every learner
 * or actor reference is elsewhere in this codebase: a mapped association
 * would place a join across what becomes a service boundary in Sprint 6.
 *
 * <p>{@code rubric} is a raw JSON string rather than a mapped object graph —
 * this entity has no business interpreting its own rubric, only storing and
 * returning it. Shape: {@code [{key, label, weight, descriptors[]}]}.
 */
@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
public class Project extends AuditableEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, unique = true, length = 220)
    private String slug;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "text")
    private String brief;

    @Column(name = "instructor_id")
    private Long instructorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "level_band", nullable = false, length = 20)
    private LevelBand levelBand = LevelBand.INTERMEDIATE;

    @Column(name = "est_minutes", nullable = false)
    private int estMinutes = 120;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String rubric = "[]";

    @Column(name = "requires_viva", nullable = false)
    private boolean requiresViva = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectStatus status = ProjectStatus.DRAFT;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Project(String title, String slug, String description) {
        this.title = title;
        this.slug = slug;
        this.description = description;
    }
}
