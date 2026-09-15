package com.skillsphere.analytics.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RiskScoreRepository extends JpaRepository<RiskScore, Long> {

    Optional<RiskScore> findFirstByUserIdOrderByComputedAtDesc(Long userId);

    /**
     * The current risk band for every learner who has one — one row per
     * user, each the most recent computation. Written as a correlated
     * subquery rather than a window function for portability and
     * readability; the table stays small enough that the cost difference
     * is immaterial at this scale.
     */
    @Query("""
           select r from RiskScore r
            where r.computedAt = (
                select max(r2.computedAt) from RiskScore r2 where r2.userId = r.userId
            )
            order by r.score desc
           """)
    List<RiskScore> findLatestPerUser();

    @Query("""
           select r from RiskScore r
            where r.band in (MEDIUM, HIGH)
              and r.computedAt = (select max(r2.computedAt) from RiskScore r2 where r2.userId = r.userId)
            order by r.score desc
           """)
    List<RiskScore> findLatestFlagged();
}
