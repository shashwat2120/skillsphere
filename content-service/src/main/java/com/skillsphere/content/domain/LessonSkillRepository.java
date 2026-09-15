package com.skillsphere.content.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LessonSkillRepository extends JpaRepository<LessonSkill, LessonSkill.Key> {

    List<LessonSkill> findByLessonId(Long lessonId);

    List<LessonSkill> findBySkillId(Long skillId);

    void deleteByLessonId(Long lessonId);
}
