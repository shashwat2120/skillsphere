package com.skillsphere.assessment.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MisconceptionRepository extends JpaRepository<Misconception, Long> {

    List<Misconception> findBySkillId(Long skillId);

    /**
     * The misconceptions a cohort holds most often.
     *
     * <p>Read as a teaching signal rather than a learner one. A belief that keeps
     * appearing across many people is being produced by the explanation, not by
     * the learners — and that is a finding no correct/incorrect tally can ever
     * surface.
     */
    @Query("select m from Misconception m where m.timesObserved > 0 order by m.timesObserved desc")
    List<Misconception> findMostObserved();

    @Query("select m from Misconception m where m.skillId in :skillIds")
    List<Misconception> findBySkillIds(@Param("skillIds") List<Long> skillIds);
}
