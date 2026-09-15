package com.skillsphere.content.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Maps a lesson onto the skills it teaches.
 *
 * <p>The single most important join in the content module. Without it, content
 * and the skill graph are two disconnected systems: the engine knows a learner
 * needs Collections but has no way to find anything that teaches it, and the
 * personalised path degenerates into a list of skill names with no material
 * behind them.
 *
 * <p>{@code weight} expresses how much of a lesson is about a given skill. A
 * lesson usually touches several, and treating a passing mention the same as the
 * main subject would send learners to material that barely addresses their gap.
 */
@Entity
@Table(name = "lesson_skills")
@IdClass(LessonSkill.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class LessonSkill {

    @Id
    @Column(name = "lesson_id")
    private Long lessonId;

    @Id
    @Column(name = "skill_id")
    private Long skillId;

    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal weight = BigDecimal.ONE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public LessonSkill(Long lessonId, Long skillId, BigDecimal weight) {
        this.lessonId = lessonId;
        this.skillId = skillId;
        this.weight = weight;
    }

    /** Composite key. The pair is the identity — a lesson maps to a skill once. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Key implements Serializable {
        private Long lessonId;
        private Long skillId;

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key key)) return false;
            return Objects.equals(lessonId, key.lessonId) && Objects.equals(skillId, key.skillId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(lessonId, skillId);
        }
    }
}
