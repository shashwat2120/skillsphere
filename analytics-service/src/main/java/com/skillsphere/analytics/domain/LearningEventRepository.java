package com.skillsphere.analytics.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LearningEventRepository extends JpaRepository<LearningEvent, Long> {

    List<LearningEvent> findByUserIdAndOccurredAtAfterOrderByOccurredAtDesc(Long userId, Instant since);

    /** Distinct users who have produced at least one event since the given instant — the active cohort. */
    @Query("select distinct e.userId from LearningEvent e where e.occurredAt >= :since")
    List<Long> findActiveUserIds(@Param("since") Instant since);

    /** Every user who has ever produced an event — risk scoring's candidate pool, active or not. */
    @Query("select distinct e.userId from LearningEvent e")
    List<Long> findAllUserIds();

    Long countByOccurredAtAfter(Instant since);

    Long countByCorrectTrueAndOccurredAtAfter(Instant since);

    /** This learner's very first recorded event — the basis for the early-enrolment-window factor. */
    Optional<LearningEvent> findFirstByUserIdOrderByOccurredAtAsc(Long userId);

    /** Most recent responses for one learner — the raw material risk scoring reasons over. */
    @Query("""
           select e from LearningEvent e
            where e.userId = :userId and e.eventType = 'ITEM_ANSWERED'
            order by e.occurredAt desc
           """)
    List<LearningEvent> findRecentResponses(@Param("userId") Long userId, org.springframework.data.domain.Pageable page);
}
