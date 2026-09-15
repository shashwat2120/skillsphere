package com.skillsphere.skill.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LearnerSkillStateRepository extends JpaRepository<LearnerSkillState, Long> {

    Optional<LearnerSkillState> findByUserIdAndSkillId(Long userId, Long skillId);

    List<LearnerSkillState> findByUserId(Long userId);

    /**
     * Loads several skill states at once.
     *
     * <p>Exists specifically to keep the adaptive engine off the N+1 path.
     * Scoring an assessment touches every skill its items measured, and fetching
     * those one at a time would issue a query per item on the hot path of every
     * answer submission.
     */
    @Query("select s from LearnerSkillState s where s.userId = :userId and s.skill.id in :skillIds")
    List<LearnerSkillState> findByUserIdAndSkillIds(@Param("userId") Long userId,
                                                    @Param("skillIds") List<Long> skillIds);

    /**
     * Whether every one of these skills is mastered by this learner.
     *
     * <p>Counts matching rows rather than returning them: the caller only needs
     * to know if the gate is satisfied, and comparing a count against the input
     * size answers that without materialising anything. Note this correctly
     * treats a missing row as unmastered, since a learner who has never touched a
     * skill has no row at all.
     */
    @Query("""
           select count(s) from LearnerSkillState s
            where s.userId = :userId
              and s.skill.id in :skillIds
              and s.masteryProbability >= :threshold
           """)
    long countMastered(@Param("userId") Long userId,
                       @Param("skillIds") List<Long> skillIds,
                       @Param("threshold") java.math.BigDecimal threshold);

    /** The learner's strongest skills, for the passport summary. */
    @Query("""
           select s from LearnerSkillState s
            where s.userId = :userId
              and s.masteryProbability >= :threshold
            order by s.masteryProbability desc
           """)
    List<LearnerSkillState> findMasteredByUserId(@Param("userId") Long userId,
                                                 @Param("threshold") java.math.BigDecimal threshold);
}
