package com.skillsphere.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * For rows that are updated in place and carry both timestamps.
 *
 * <p>Timestamps are {@link Instant}, mapped to {@code TIMESTAMPTZ}. Storing an
 * instant rather than a local date-time is not a style preference: learners,
 * instructors and reviewers are in different time zones, streaks are computed
 * against a learner's own day boundary, and evidence carries a verification
 * time that an employer may inspect years later. A timestamp without a zone
 * makes every one of those ambiguous.
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity extends BaseEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
