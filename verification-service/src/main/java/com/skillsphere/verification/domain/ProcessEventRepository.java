package com.skillsphere.verification.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProcessEventRepository extends JpaRepository<ProcessEvent, Long> {

    List<ProcessEvent> findBySubmissionIdOrderByOccurredAt(Long submissionId);

    long countBySubmissionIdAndEventType(Long submissionId, ProcessEventType eventType);
}
