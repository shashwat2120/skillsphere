package com.skillsphere.skill.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SkillCategoryRepository extends JpaRepository<SkillCategory, Long> {

    Optional<SkillCategory> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<SkillCategory> findAllByOrderByPositionAsc();
}
