package com.skillsphere.skill.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkillMasteryHistoryRepository extends JpaRepository<SkillMasteryHistory, Long> {

    /** The trail behind one skill claim, newest first. */
    List<SkillMasteryHistory> findByUserIdAndSkillIdOrderByRecordedAtDesc(Long userId, Long skillId);

    List<SkillMasteryHistory> findByUserIdOrderByRecordedAtDesc(Long userId);
}
