package com.skillsphere.analytics.internal;

import java.time.Instant;

/**
 * This service's own copy of the wire shape assessment publishes to the
 * {@code assessment-events} Kafka topic — the field-for-field twin of
 * assessment's in-process {@code ResponseRecorded} record, deliberately not
 * a shared library between the two services. See identity-service's
 * IdentityEventEnvelope for the full reasoning; the same trade-off applies
 * here at a smaller scale, since assessment (unlike identity) currently
 * publishes only this one event type.
 */
public record ResponseRecordedEvent(
        Long userId,
        Long skillId,
        Long itemId,
        boolean correct,
        Integer responseTimeMs,
        Instant occurredAt) {
}
