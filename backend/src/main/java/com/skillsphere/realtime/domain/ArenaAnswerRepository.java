package com.skillsphere.realtime.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArenaAnswerRepository extends JpaRepository<ArenaAnswer, Long> {

    Optional<ArenaAnswer> findByArenaQuestionIdAndParticipantId(Long arenaQuestionId, Long participantId);

    List<ArenaAnswer> findByArenaQuestionId(Long arenaQuestionId);
}
