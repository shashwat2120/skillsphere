package com.skillsphere.skill.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Graph traversal over the prerequisite edges.
 *
 * <p>Every traversal here is a single recursive CTE rather than a walk through
 * JPA associations. The difference is not stylistic: "what does this skill
 * transitively depend on?" is unbounded in depth, and answering it through
 * mapped collections issues one query per node per level. On a graph of a few
 * hundred skills that is hundreds of round trips for a question Postgres answers
 * in one.
 *
 * <p><b>Two safeguards appear in every recursive query.</b> {@code UNION} rather
 * than {@code UNION ALL} deduplicates, so a node already visited is not expanded
 * again; and an explicit depth ceiling stops the recursion even if a cycle
 * somehow reached the table. Cycles are prevented on insert, but a traversal that
 * can hang the database if that guard is ever bypassed is not a traversal worth
 * shipping — the cost of both safeguards is negligible and the failure they
 * prevent is total.
 */
public interface SkillPrerequisiteRepository extends JpaRepository<SkillPrerequisite, Long> {

    List<SkillPrerequisite> findBySkillId(Long skillId);

    List<SkillPrerequisite> findByPrerequisiteSkillId(Long prerequisiteSkillId);

    boolean existsBySkillIdAndPrerequisiteSkillId(Long skillId, Long prerequisiteSkillId);

    Optional<SkillPrerequisite> findBySkillIdAndPrerequisiteSkillId(Long skillId, Long prerequisiteSkillId);

    void deleteBySkillIdAndPrerequisiteSkillId(Long skillId, Long prerequisiteSkillId);

    /**
     * Every skill this one transitively depends on.
     *
     * <p>Used to answer "what would I have to learn first?" and, more
     * importantly, to detect cycles: adding an edge is unsafe precisely when the
     * proposed prerequisite already has the skill among its own ancestors.
     */
    @Query(value = """
            WITH RECURSIVE ancestors AS (
                SELECT sp.prerequisite_skill_id AS skill_id, 1 AS depth
                  FROM skill_prerequisites sp
                 WHERE sp.skill_id = :skillId
                UNION
                SELECT sp.prerequisite_skill_id, a.depth + 1
                  FROM skill_prerequisites sp
                  JOIN ancestors a ON sp.skill_id = a.skill_id
                 WHERE a.depth < :maxDepth
            )
            SELECT DISTINCT skill_id FROM ancestors
            """, nativeQuery = true)
    List<Long> findAncestorIds(@Param("skillId") Long skillId, @Param("maxDepth") int maxDepth);

    /**
     * Every skill that transitively depends on this one — what mastering it
     * unlocks.
     *
     * <p>Drives the skill-tree view: completing a node lights up the edges to
     * everything it opens, which is the moment that makes the graph feel like
     * progress rather than a diagram.
     */
    @Query(value = """
            WITH RECURSIVE descendants AS (
                SELECT sp.skill_id AS skill_id, 1 AS depth
                  FROM skill_prerequisites sp
                 WHERE sp.prerequisite_skill_id = :skillId
                UNION
                SELECT sp.skill_id, d.depth + 1
                  FROM skill_prerequisites sp
                  JOIN descendants d ON sp.prerequisite_skill_id = d.skill_id
                 WHERE d.depth < :maxDepth
            )
            SELECT DISTINCT skill_id FROM descendants
            """, nativeQuery = true)
    List<Long> findDescendantIds(@Param("skillId") Long skillId, @Param("maxDepth") int maxDepth);

    /**
     * Whether {@code candidatePrerequisiteId} can be added as a prerequisite of
     * {@code skillId} without closing a cycle.
     *
     * <p>Returns true when the proposed edge is unsafe. The test is reachability:
     * the edge says the prerequisite must come before the skill, so it is a
     * contradiction exactly when the skill already has to come before the
     * prerequisite — that is, when the skill appears among the prerequisite's own
     * ancestors.
     *
     * <p>Deliberately one query rather than loading the graph and walking it in
     * Java. Under concurrent edits, a check performed against an in-memory copy
     * can be made stale by another transaction before the insert lands.
     */
    @Query(value = """
            WITH RECURSIVE ancestors AS (
                SELECT sp.prerequisite_skill_id AS skill_id, 1 AS depth
                  FROM skill_prerequisites sp
                 WHERE sp.skill_id = :candidatePrerequisiteId
                UNION
                SELECT sp.prerequisite_skill_id, a.depth + 1
                  FROM skill_prerequisites sp
                  JOIN ancestors a ON sp.skill_id = a.skill_id
                 WHERE a.depth < :maxDepth
            )
            SELECT EXISTS (SELECT 1 FROM ancestors WHERE skill_id = :skillId)
            """, nativeQuery = true)
    boolean wouldCreateCycle(@Param("skillId") Long skillId,
                             @Param("candidatePrerequisiteId") Long candidatePrerequisiteId,
                             @Param("maxDepth") int maxDepth);

    /**
     * Direct prerequisites that block progression, with their ids only.
     *
     * <p>Hard gates alone: soft edges advise an ordering but must never stop a
     * learner reaching a skill, or the graph collapses into a single fixed
     * sequence.
     */
    @Query(value = """
            SELECT sp.prerequisite_skill_id
              FROM skill_prerequisites sp
             WHERE sp.skill_id = :skillId
               AND sp.strength >= 0.99
            """, nativeQuery = true)
    List<Long> findHardPrerequisiteIds(@Param("skillId") Long skillId);
}
