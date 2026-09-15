package com.skillsphere.assessment.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssessmentRepository extends JpaRepository<Assessment, Long> {

    /**
     * The learner's in-flight assessment, if there is one.
     *
     * <p>Used to resume rather than restart. Someone whose connection drops nine
     * questions into a diagnostic should carry on — starting again would discard
     * nine honest responses and ask them to prove the same things twice.
     */
    Optional<Assessment> findByUserIdAndStatus(Long userId, AssessmentStatus status);

    List<Assessment> findByUserIdOrderByStartedAtDesc(Long userId);
}
