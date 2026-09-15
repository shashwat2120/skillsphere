package com.skillsphere.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One learner, one day, pre-aggregated.
 *
 * <p>Updated incrementally as events arrive rather than computed by a
 * nightly batch — {@link com.skillsphere.analytics.internal.ResponseEventListener}
 * upserts today's row on every response instead of scanning
 * {@code learning_events} on a schedule. For a rollup this cheap to compute
 * (one row, a handful of counters) a batch job would only add latency
 * between an action happening and a dashboard reflecting it, which matters
 * more for a live classroom than the marginal write cost saved by batching.
 */
@Entity
@Table(name = "daily_activity")
@Getter
@Setter
@NoArgsConstructor
public class DailyActivity {

    @EmbeddedId
    private DailyActivityId id;

    @Column(name = "minutes_active", nullable = false)
    private int minutesActive = 0;

    @Column(name = "items_answered", nullable = false)
    private int itemsAnswered = 0;

    @Column(name = "items_correct", nullable = false)
    private int itemsCorrect = 0;

    @Column(name = "lessons_completed", nullable = false)
    private int lessonsCompleted = 0;

    @Column(name = "xp_earned", nullable = false)
    private int xpEarned = 0;

    @Column(name = "sessions_count", nullable = false)
    private int sessionsCount = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public DailyActivity(DailyActivityId id) {
        this.id = id;
    }

    public void recordResponse(boolean correct) {
        itemsAnswered++;
        if (correct) {
            itemsCorrect++;
        }
        updatedAt = Instant.now();
    }
}
