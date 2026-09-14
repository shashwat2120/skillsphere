package com.skillsphere.realtime.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArenaRepository extends JpaRepository<Arena, Long> {

    Optional<Arena> findByJoinCode(String joinCode);

    List<Arena> findByInstructorIdOrderByCreatedAtDesc(Long instructorId);
}
