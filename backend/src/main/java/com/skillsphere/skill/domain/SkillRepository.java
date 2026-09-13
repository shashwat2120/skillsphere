package com.skillsphere.skill.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SkillRepository extends JpaRepository<Skill, Long> {

    Optional<Skill> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Skill> findByActiveTrueOrderByNameAsc();

    List<Skill> findByCategoryIdAndActiveTrueOrderByNameAsc(Long categoryId);

    /**
     * The frontier: skills this learner is ready to start right now.
     *
     * <p>This is the query the whole product turns on. A skill qualifies when the
     * learner has not mastered it and every <em>hard</em> prerequisite is already
     * mastered. Soft edges are ignored here on purpose — they advise an ordering,
     * and letting them block would reduce the graph to one rigid sequence, which
     * is the sequential unlocking this platform exists to replace.
     *
     * <p><b>Why it is one query.</b> The obvious implementation loads candidate
     * skills, then for each loads its prerequisites, then for each of those loads
     * the learner's mastery — a textbook N+1 that costs hundreds of round trips
     * per page load. Here the whole thing is a single scan with two correlated
     * subqueries, and it sits on the hot path of every dashboard render.
     *
     * <p><b>Why COALESCE.</b> A learner has no {@code learner_skill_state} row
     * until they first touch a skill. Treating a missing row as zero mastery is
     * correct and avoids having to pre-create a row per learner per skill, which
     * on a few hundred skills would mean writing hundreds of rows at signup for
     * skills most people will never attempt.
     */
    @Query(value = """
            SELECT s.*
              FROM skills s
             WHERE s.is_active
               AND COALESCE((SELECT lss.mastery_probability
                               FROM learner_skill_state lss
                              WHERE lss.user_id = :userId
                                AND lss.skill_id = s.id), 0) < :masteryThreshold
               AND NOT EXISTS (
                     SELECT 1
                       FROM skill_prerequisites sp
                      WHERE sp.skill_id = s.id
                        AND sp.strength >= 0.99
                        AND COALESCE((SELECT lss2.mastery_probability
                                        FROM learner_skill_state lss2
                                       WHERE lss2.user_id = :userId
                                         AND lss2.skill_id = sp.prerequisite_skill_id), 0)
                            < :masteryThreshold
                   )
             ORDER BY s.level_band, s.name
            """, nativeQuery = true)
    List<Skill> findReadyToLearn(@Param("userId") Long userId,
                                 @Param("masteryThreshold") double masteryThreshold);

    /**
     * Skills the learner has mastered but has not practised recently.
     *
     * <p>Feeds the decay refresher. Skills with {@code decay_rate = 0} are
     * exempt — some knowledge genuinely does not fade, and nagging someone to
     * revise something durable trains them to ignore the platform's prompts,
     * which costs more than the reminder gains.
     */
    @Query(value = """
            SELECT s.*
              FROM skills s
              JOIN learner_skill_state lss ON lss.skill_id = s.id
             WHERE lss.user_id = :userId
               AND s.is_active
               AND s.decay_rate > 0
               AND lss.mastery_probability >= :masteryThreshold
               AND lss.last_practiced_at < now() - make_interval(days => :staleDays)
             ORDER BY lss.last_practiced_at
            """, nativeQuery = true)
    List<Skill> findDecayCandidates(@Param("userId") Long userId,
                                    @Param("masteryThreshold") double masteryThreshold,
                                    @Param("staleDays") int staleDays);

    /**
     * Roots of the graph — skills with no prerequisites at all.
     *
     * <p>Where a learner with no history starts, and the entry points the skill
     * tree renders first.
     */
    @Query(value = """
            SELECT s.*
              FROM skills s
             WHERE s.is_active
               AND NOT EXISTS (SELECT 1 FROM skill_prerequisites sp WHERE sp.skill_id = s.id)
             ORDER BY s.name
            """, nativeQuery = true)
    List<Skill> findRootSkills();
}
