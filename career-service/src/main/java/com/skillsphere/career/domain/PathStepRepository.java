package com.skillsphere.career.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PathStepRepository extends JpaRepository<PathStep, Long> {

    List<PathStep> findByLearningPathIdOrderByPosition(Long learningPathId);
}
