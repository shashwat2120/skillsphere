package com.skillsphere.career.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleSkillRequirementRepository extends JpaRepository<RoleSkillRequirement, Long> {

    List<RoleSkillRequirement> findByCareerRoleId(Long careerRoleId);
}
