package com.skillsphere.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A durable record of one instructor application, separate from
 * {@code users.status}.
 *
 * <p>{@code User.status} answers "can this account sign in right now" and is
 * what {@link com.skillsphere.identity.internal.UserAdminService} still flips
 * on approve/reject — that behaviour is unchanged. This table answers a
 * different question that the status flip alone cannot: <em>when</em> did
 * someone apply, <em>who</em> reviewed it, <em>when</em>, and <em>why</em>.
 * Without it, rejecting and reapplying leaves no trace of the earlier
 * decision, and "who approved this instructor" is unanswerable a week later.
 */
@Entity
@Table(name = "instructor_applications")
@Getter
@Setter
@NoArgsConstructor
public class InstructorApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "applied_at", nullable = false, updatable = false)
    private Instant appliedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(columnDefinition = "text")
    private String notes;

    public InstructorApplication(Long userId) {
        this.userId = userId;
        this.status = Status.PENDING;
        this.appliedAt = Instant.now();
    }

    public void approve(Long reviewerId) {
        this.status = Status.APPROVED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = Instant.now();
    }

    public void reject(Long reviewerId, String reason) {
        this.status = Status.REJECTED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = Instant.now();
        this.notes = reason;
    }

    public enum Status {
        PENDING, APPROVED, REJECTED
    }
}
