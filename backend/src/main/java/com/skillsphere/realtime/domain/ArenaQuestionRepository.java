package com.skillsphere.realtime.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArenaQuestionRepository extends JpaRepository<ArenaQuestion, Long> {

    List<ArenaQuestion> findByArenaIdOrderByPosition(Long arenaId);

    Optional<ArenaQuestion> findByArenaIdAndPosition(Long arenaId, int position);
}
