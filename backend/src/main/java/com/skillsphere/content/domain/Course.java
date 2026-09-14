package com.skillsphere.content.domain;

import com.skillsphere.shared.domain.AuditableEntity;
import com.skillsphere.shared.domain.LevelBand;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A course: a container of teaching material.
 *
 * <p>Deliberately not the centre of this system. In a normal LMS the course is
 * the product; here it is <em>evidence-producing material</em> that exists to
 * serve skills. Nothing in the adaptive engine reads a course — it routes on the
 * skill graph, and reaches content only through {@link LessonSkill}. A learner
 * can master a skill without touching any course at all, by demonstrating it.
 *
 * <p>The instructor is referenced by id, not as a mapped User. Same rule as the
 * skill module: a JPA association here would become a join across a service
 * boundary in Sprint 6.
 */
@Entity
@Table(name = "courses")
@Getter
@Setter
@NoArgsConstructor
public class Course extends AuditableEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, unique = true, length = 220)
    private String slug;

    @Column(length = 300)
    private String subtitle;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    /** Owner. Ownership is enforced in the service, never assumed by the caller. */
    @Column(name = "instructor_id", nullable = false)
    private Long instructorId;

    @Column(name = "category_id")
    private Long categoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "level_band", nullable = false, length = 20)
    private LevelBand levelBand = LevelBand.INTERMEDIATE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourseStatus status = CourseStatus.DRAFT;

    @Column(name = "est_minutes", nullable = false)
    private int estMinutes = 0;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Course(String title, String slug, Long instructorId) {
        this.title = title;
        this.slug = slug;
        this.instructorId = instructorId;
    }

    public boolean isOwnedBy(Long userId) {
        return instructorId.equals(userId);
    }

    public void publish() {
        this.status = CourseStatus.PUBLISHED;
        // Set once. Re-publishing after an archive should not rewrite history —
        // "first published" is a fact about the course, not about the last edit.
        if (publishedAt == null) {
            this.publishedAt = Instant.now();
        }
    }

    public void archive() {
        this.status = CourseStatus.ARCHIVED;
    }
}
