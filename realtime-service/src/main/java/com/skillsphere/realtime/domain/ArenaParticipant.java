package com.skillsphere.realtime.domain;

import com.skillsphere.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One participant in one arena — signed in, or a guest.
 *
 * <p>{@code userId} and {@code guestToken} are mutually exclusive but not
 * mapped as a proper either/or type: the database's
 * {@code CHECK (user_id IS NOT NULL OR guest_token IS NOT NULL)} is the real
 * guarantee, and duplicating that as a Java sum type here would buy nothing a
 * constructor invariant doesn't already give.
 */
@Entity
@Table(name = "arena_participants")
@Getter
@Setter
@NoArgsConstructor
public class ArenaParticipant extends BaseEntity {

    @Column(name = "arena_id", nullable = false)
    private Long arenaId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "display_name", nullable = false, length = 60)
    private String displayName;

    @Column(name = "guest_token", length = 64)
    private String guestToken;

    @Column(nullable = false)
    private int score = 0;

    @Column(name = "correct_count", nullable = false)
    private int correctCount = 0;

    @Column(name = "answer_count", nullable = false)
    private int answerCount = 0;

    @Column(name = "final_rank")
    private Integer finalRank;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt = Instant.now();

    @Column(name = "left_at")
    private Instant leftAt;

    public ArenaParticipant(Long arenaId, Long userId, String guestToken, String displayName) {
        this.arenaId = arenaId;
        this.userId = userId;
        this.guestToken = guestToken;
        this.displayName = displayName;
    }

    public void recordAnswer(boolean correct, int points) {
        answerCount++;
        if (correct) {
            correctCount++;
        }
        score += points;
    }
}
