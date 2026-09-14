package com.skillsphere.content.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {

    Optional<Course> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query("select c from Course c where c.instructorId = :instructorId and c.deletedAt is null")
    List<Course> findByInstructor(@Param("instructorId") Long instructorId);

    /**
     * The public catalogue.
     *
     * <p>Archived courses are excluded here but remain readable by anyone already
     * enrolled. Archiving means "stop new people joining", not "delete the thing
     * learners are partway through" — unpublishing in a way that stranded
     * existing learners would punish them for an instructor's decision.
     */
    @Query("""
           select c from Course c
            where c.status = com.skillsphere.content.domain.CourseStatus.PUBLISHED
              and c.deletedAt is null
            order by c.publishedAt desc
           """)
    List<Course> findPublished();
}
