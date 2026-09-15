package com.skillsphere.career.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LearningPathRepository extends JpaRepository<LearningPath, Long> {

    /**
     * Scoped by role, not just by user — matching what the database actually
     * guarantees ({@code uq_lp_one_active} is unique on
     * {@code (user_id, career_role_id)} where active, not on {@code user_id}
     * alone, because a learner may in principle hold paths toward more than one
     * role). An {@code Optional}-returning finder whose assumption is narrower
     * than the constraint behind it is exactly how the diagnostic assessment
     * race happened earlier this session — scoping this one correctly from the
     * start avoids repeating it.
     */
    Optional<LearningPath> findByUserIdAndCareerRoleIdAndStatus(Long userId, Long careerRoleId, PathStatus status);

    List<LearningPath> findByUserIdOrderByGeneratedAtDesc(Long userId);
}
