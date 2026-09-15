package com.skillsphere.identity.internal;

import java.time.Instant;

/**
 * This service's own copy of the wire shape assessment-service publishes to
 * the {@code admin-audit-events} Kafka topic — the field-for-field twin of
 * assessment's in-process {@code AdminActionRecorded} record, deliberately
 * not a shared library between the two services. See identity-service's own
 * IdentityEventEnvelope for the full reasoning behind that trade-off.
 */
public record AdminActionRecordedEvent(
        Long adminId,
        String action,
        String targetType,
        Long targetId,
        String beforeState,
        String afterState,
        String ipAddress,
        String reason,
        Instant occurredAt) {
}
