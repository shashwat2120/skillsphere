package com.skillsphere.verification.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectSkillRepository extends JpaRepository<ProjectSkill, ProjectSkillId> {

    @Query("select ps from ProjectSkill ps where ps.id.projectId = :projectId")
    List<ProjectSkill> findByProjectId(@Param("projectId") Long projectId);
}
