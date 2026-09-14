package com.skillsphere.career.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LearnerCareerGoalRepository extends JpaRepository<LearnerCareerGoal, Long> {

    Optional<LearnerCareerGoal> findByUserIdAndPrimaryTrue(Long userId);

    Optional<LearnerCareerGoal> findByUserIdAndCareerRoleId(Long userId, Long careerRoleId);
}
