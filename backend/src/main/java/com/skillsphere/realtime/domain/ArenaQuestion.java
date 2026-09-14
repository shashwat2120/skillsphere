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
 * One item, at one position, in one arena's fixed running order.
 *
 * <p>{@code publishedAt} is what turns "exists" into "live" — a row is
 * created for every question when the arena is set up, but participants
 * only see the stem and options once this is set, which is the moment the
 * instructor advances to it.
 */
@Entity
@Table(name = "arena_questions")
@Getter
@Setter
@NoArgsConstructor
public class ArenaQuestion extends BaseEntity {

    @Column(name = "arena_id", nullable = false)
    private Long arenaId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private int position;

    @Column(name = "time_limit_sec", nullable = false)
    private int timeLimitSec = 20;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    public ArenaQuestion(Long arenaId, Long itemId, int position, int timeLimitSec) {
        this.arenaId = arenaId;
        this.itemId = itemId;
        this.position = position;
        this.timeLimitSec = timeLimitSec;
    }

    public void publish() {
        this.publishedAt = Instant.now();
    }

    public void close() {
        this.closedAt = Instant.now();
    }

    public boolean isOpen() {
        return publishedAt != null && closedAt == null;
    }
}
