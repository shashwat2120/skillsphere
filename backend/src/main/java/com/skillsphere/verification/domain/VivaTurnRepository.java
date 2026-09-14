package com.skillsphere.verification.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VivaTurnRepository extends JpaRepository<VivaTurn, Long> {

    List<VivaTurn> findByVivaSessionIdOrderByPosition(Long vivaSessionId);

    Optional<VivaTurn> findByVivaSessionIdAndPosition(Long vivaSessionId, int position);
}
