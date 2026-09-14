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
 * Raised when a learner fails several related items inside a live session —
 * the AI-plus-human loop this product is built around. The system does not
 * intervene itself; it tells the instructor exactly where to look while the
 * learner is still stuck, which is the only moment the alert is useful.
 */
@Entity
@Table(name = "confusion_signals")
@Getter
@Setter
@NoArgsConstructor
public class ConfusionSignal extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "misconception_id")
    private Long misconceptionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConfusionSeverity severity = ConfusionSeverity.MEDIUM;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt = Instant.now();

    @Column(name = "instructor_notified_at")
    private Instant instructorNotifiedAt;

    @Column(name = "acknowledged_by")
    private Long acknowledgedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public ConfusionSignal(Long userId, Long skillId, int failureCount,
                            ConfusionSeverity severity, Instant windowStart) {
        this.userId = userId;
        this.skillId = skillId;
        this.failureCount = failureCount;
        this.severity = severity;
        this.windowStart = windowStart;
    }

    public void markNotified() {
        this.instructorNotifiedAt = Instant.now();
    }
}
