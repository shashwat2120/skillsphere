package com.skillsphere.assessment.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ResponseRepository extends JpaRepository<Response, Long> {

    List<Response> findByAssessmentIdOrderByAnsweredAt(Long assessmentId);

    /**
     * Recent wrong answers for one learner in one skill.
     *
     * <p>Feeds confusion detection. Several failures on the same skill inside a
     * short window is a different signal from a low overall score: it means
     * somebody is stuck <em>right now</em>, which is the only moment an
     * instructor can usefully intervene.
     */
    @Query("""
           select r from Response r
            where r.userId = :userId
              and r.item.skillId = :skillId
              and r.correct = false
              and r.answeredAt > :since
            order by r.answeredAt desc
           """)
    List<Response> findRecentIncorrect(@Param("userId") Long userId,
                                       @Param("skillId") Long skillId,
                                       @Param("since") Instant since);

    long countByUserIdAndItemSkillId(Long userId, Long skillId);
}
