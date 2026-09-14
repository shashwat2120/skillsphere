package com.skillsphere.verification.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenceRepository extends JpaRepository<Evidence, Long> {

    List<Evidence> findByUserIdOrderByVerifiedAtDesc(Long userId);

    boolean existsBySourceTypeAndSourceIdAndSkillId(EvidenceSourceType sourceType, Long sourceId, Long skillId);
}
