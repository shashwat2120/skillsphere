package com.skillsphere.realtime.domain;

import com.skillsphere.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One live session: a room, a join code, a fixed set of questions everyone
 * answers together.
 *
 * <p>{@code joinCode} rather than {@code id} is what a QR code or a typed
 * code encodes — short, human-typable, and disposable, so a code from last
 * week's arena can't be reused to imply anything about this week's.
 */
@Entity
@Table(name = "arenas")
@Getter
@Setter
@NoArgsConstructor
public class Arena extends BaseEntity {

    @Column(name = "instructor_id", nullable = false)
    private Long instructorId;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "skill_id")
    private Long skillId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "join_code", nullable = false, unique = true, length = 10)
    private String joinCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ArenaStatus status = ArenaStatus.LOBBY;

    @Column(name = "allow_guests", nullable = false)
    private boolean allowGuests = true;

    @Column(name = "question_count", nullable = false)
    private int questionCount = 10;

    @Column(name = "seconds_per_q", nullable = false)
    private int secondsPerQuestion = 20;

    @Column(name = "participant_count", nullable = false)
    private int participantCount = 0;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Arena(Long instructorId, Long skillId, String title, String joinCode,
                 int questionCount, int secondsPerQuestion) {
        this.instructorId = instructorId;
        this.skillId = skillId;
        this.title = title;
        this.joinCode = joinCode;
        this.questionCount = questionCount;
        this.secondsPerQuestion = secondsPerQuestion;
    }

    public void start() {
        this.status = ArenaStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    public void end() {
        this.status = ArenaStatus.ENDED;
        this.endedAt = Instant.now();
    }

    public boolean isJoinable() {
        return status == ArenaStatus.LOBBY;
    }
}
