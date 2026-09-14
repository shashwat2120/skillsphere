package com.skillsphere.content.domain;

import com.skillsphere.shared.domain.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single piece of teaching material.
 *
 * <p>Only becomes reachable by the path engine once it is tagged to at least one
 * skill through {@link LessonSkill}. An untagged lesson is invisible to routing —
 * it can still be read by someone browsing a course, but the engine has no reason
 * to ever recommend it, because it cannot say what gap the lesson would close.
 */
@Entity
@Table(name = "lessons")
@Getter
@Setter
@NoArgsConstructor
public class Lesson extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "module_id", nullable = false)
    private CourseModule module;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LessonType type = LessonType.TEXT;

    @Column(columnDefinition = "text")
    private String content;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @Column(name = "resource_url", length = 500)
    private String resourceUrl;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds = 0;

    @Column(nullable = false)
    private int position = 0;

    /**
     * Readable without enrolment.
     *
     * <p>A catalogue that shows nothing until you commit gives a learner no way
     * to judge whether the material suits them, which is a bigger cause of early
     * dropout than difficulty.
     */
    @Column(name = "is_preview", nullable = false)
    private boolean preview = false;

    public Lesson(CourseModule module, String title, int position) {
        this.module = module;
        this.title = title;
        this.position = position;
    }
}
