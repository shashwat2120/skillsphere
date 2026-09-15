package com.skillsphere.verification.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiUsageDeclarationRepository extends JpaRepository<AiUsageDeclaration, Long> {

    Optional<AiUsageDeclaration> findFirstBySubmissionIdOrderByIdDesc(Long submissionId);
}
