package com.skillsphere.assessment.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItemRepository extends JpaRepository<Item, Long> {

    List<Item> findBySkillIdAndStatus(Long skillId, ItemStatus status);

    List<Item> findByAuthorId(Long authorId);

    /**
     * Active items for a skill, excluding ones this learner has already seen.
     *
     * <p>Exposure control, and it is not optional. Without it an adaptive engine
     * converges on whichever item is most informative and serves it repeatedly —
     * the learner sees the same question every session, memorises the answer, and
     * their measured ability rises while their actual ability does not. The
     * engine would then confidently certify a skill nobody has.
     */
    @Query(value = """
            SELECT i.* FROM items i
             WHERE i.skill_id = :skillId
               AND i.status = 'ACTIVE'
               AND i.deleted_at IS NULL
               AND NOT EXISTS (
                     SELECT 1 FROM responses r
                      WHERE r.item_id = i.id AND r.user_id = :userId)
            """, nativeQuery = true)
    List<Item> findUnseenBySkill(@Param("skillId") Long skillId, @Param("userId") Long userId);

    /**
     * Items whose measured behaviour suggests they are broken.
     *
     * <p>Three failure modes, all invisible without this query: almost everyone
     * answers correctly (measures nothing), almost nobody does (likely wrong key
     * or unteachable), or discrimination has collapsed (strong and weak learners
     * do equally well, which usually means the wording is the obstacle rather
     * than the concept).
     */
    @Query(value = """
            SELECT i.* FROM items i
             WHERE i.status = 'ACTIVE'
               AND i.times_seen >= :minResponses
               AND (
                     i.times_correct::numeric / i.times_seen > 0.95
                  OR i.times_correct::numeric / i.times_seen < 0.15
                  OR i.discrimination_a < 0.3
               )
             ORDER BY i.times_seen DESC
            """, nativeQuery = true)
    List<Item> findSuspectItems(@Param("minResponses") int minResponses);

    long countBySkillIdAndStatus(Long skillId, ItemStatus status);
}
