package com.skillsphere.verification.domain;

import com.skillsphere.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * One entry in the process ledger — append-only, never edited.
 *
 * <p>No {@code updated_at}: a mutable audit record is not an audit record.
 * This is the row-level version of the same rule {@code BaseEntity} states
 * for the class hierarchy.
 */
@Entity
@Table(name = "process_events")
@Getter
@Setter
@NoArgsConstructor
public class ProcessEvent extends BaseEntity {

    @Column(name = "submission_id", nullable = false)
    private Long submissionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private ProcessEventType eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload = "{}";

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    public ProcessEvent(Long submissionId, ProcessEventType eventType, String payload) {
        this.submissionId = submissionId;
        this.eventType = eventType;
        this.payload = payload;
    }
}
