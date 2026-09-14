package com.skillsphere.content.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseModuleRepository extends JpaRepository<CourseModule, Long> {

    List<CourseModule> findByCourseIdOrderByPosition(Long courseId);

    long countByCourseId(Long courseId);
}
