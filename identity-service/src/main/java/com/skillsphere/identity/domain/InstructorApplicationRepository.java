package com.skillsphere.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InstructorApplicationRepository extends JpaRepository<InstructorApplication, Long> {

    List<InstructorApplication> findByStatusOrderByAppliedAtAsc(InstructorApplication.Status status);

    /**
     * The most recent application for a user — reapplying after a rejection
     * creates a new row rather than reusing the old one, so this is how
     * approve/reject find "the one currently awaiting a decision".
     */
    Optional<InstructorApplication> findFirstByUserIdOrderByAppliedAtDesc(Long userId);
}
