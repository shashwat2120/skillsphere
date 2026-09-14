package com.skillsphere.verification.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    List<Submission> findByUserIdAndProjectIdOrderByAttemptNoDesc(Long userId, Long projectId);

    /**
     * {@code findFirst...OrderBy} rather than a plain {@code Optional}-returning
     * finder: nothing in the schema actually guarantees at most one DRAFT
     * submission per (user, project) the way the diagnostic assessment race
     * assumed for IN_PROGRESS assessments earlier this session. Ordering and
     * taking the first is safe regardless of how many exist; an
     * ambiguity-throwing finder here would not be.
     */
    Optional<Submission> findFirstByUserIdAndProjectIdAndStatusOrderByAttemptNoDesc(
            Long userId, Long projectId, SubmissionStatus status);
}
