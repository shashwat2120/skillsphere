package com.skillsphere.content.domain;

import com.skillsphere.shared.domain.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A section within a course. Purely organisational — it teaches nothing itself. */
@Entity
@Table(name = "course_modules")
@Getter
@Setter
@NoArgsConstructor
public class CourseModule extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 500)
    private String summary;

    @Column(nullable = false)
    private int position = 0;

    public CourseModule(Course course, String title, int position) {
        this.course = course;
        this.title = title;
        this.position = position;
    }
}
