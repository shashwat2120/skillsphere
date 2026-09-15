package com.skillsphere.realtime.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArenaParticipantRepository extends JpaRepository<ArenaParticipant, Long> {

    List<ArenaParticipant> findByArenaIdOrderByScoreDesc(Long arenaId);

    Optional<ArenaParticipant> findByArenaIdAndUserId(Long arenaId, Long userId);

    Optional<ArenaParticipant> findByArenaIdAndGuestToken(Long arenaId, String guestToken);
}
