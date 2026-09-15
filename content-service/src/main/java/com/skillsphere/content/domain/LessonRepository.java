package com.skillsphere.content.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    List<Lesson> findByModuleIdOrderByPosition(Long moduleId);

    /**
     * Lessons that teach a given skill, best match first.
     *
     * <p>This is how the path engine turns "you are weak at Collections" into
     * something a learner can actually open. Ordering by weight matters: a
     * lesson whose main subject is the skill should come before one that mentions
     * it in passing, or the engine sends people to material that barely addresses
     * their gap.
     *
     * <p>Only published, non-deleted courses are considered — recommending a
     * learner into a draft course would expose unfinished material as if it were
     * ready.
     */
    @Query(value = """
            SELECT l.* FROM lessons l
              JOIN lesson_skills ls ON ls.lesson_id = l.id
              JOIN course_modules m ON m.id = l.module_id
              JOIN courses c        ON c.id = m.course_id
             WHERE ls.skill_id = :skillId
               AND c.status = 'PUBLISHED'
               AND c.deleted_at IS NULL
             ORDER BY ls.weight DESC, l.position
            """, nativeQuery = true)
    List<Lesson> findTeachingSkill(@Param("skillId") Long skillId);
}
