package com.skillsphere.verification.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VivaSessionRepository extends JpaRepository<VivaSession, Long> {

    /**
     * {@code findFirst...OrderBy}, not a plain {@code Optional} finder — same
     * reasoning as {@code SubmissionRepository}: nothing forces exactly one
     * IN_PROGRESS session to exist for a submission, so this stays safe
     * however many there turn out to be instead of throwing on the second.
     */
    Optional<VivaSession> findFirstBySubmissionIdAndStatusOrderByIdDesc(Long submissionId, VivaStatus status);
}
