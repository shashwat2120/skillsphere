package com.skillsphere.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * For the few tables with genuine concurrent writers, where a lost update would
 * corrupt data silently.
 *
 * <p>Optimistic locking is applied narrowly rather than everywhere, because a
 * version column costs a write conflict on every contended update and most of
 * our tables have exactly one writer.
 *
 * <p>Where it is genuinely needed:
 * <ul>
 *   <li>{@code learner_skill_state} — a learner answering in the live arena
 *       while a scheduled decay job recalculates the same row. Losing one of
 *       those updates quietly corrupts the mastery figure that a skill claim
 *       rests on.</li>
 *   <li>{@code user_progression} — XP and streaks arrive from several
 *       independent event consumers at once.</li>
 *   <li>{@code arena_participants} — scores update on every answer during a
 *       live session.</li>
 * </ul>
 *
 * <p>Callers should expect {@code OptimisticLockingFailureException} on these
 * and retry, rather than treating it as an error: a conflict means the data was
 * protected, which is the point.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class VersionedEntity extends AuditableEntity {

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
