package com.skillsphere.verification.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findBySlug(String slug);

    @Query("select p from Project p where p.status = 'PUBLISHED' and p.deletedAt is null order by p.title")
    List<Project> findAllPublished();
}
