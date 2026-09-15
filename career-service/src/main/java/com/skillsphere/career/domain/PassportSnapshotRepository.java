package com.skillsphere.career.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PassportSnapshotRepository extends JpaRepository<PassportSnapshot, Long> {

    Optional<PassportSnapshot> findByShareToken(String shareToken);

    List<PassportSnapshot> findByUserIdOrderByCreatedAtDesc(Long userId);
}
