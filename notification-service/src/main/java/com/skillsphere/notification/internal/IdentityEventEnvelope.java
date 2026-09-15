package com.skillsphere.notification.internal;

import java.time.Instant;

/**
 * This module's own copy of the wire shape identity-service publishes to the
 * {@code identity-events} Kafka topic.
 *
 * <p>Deliberately not a shared library between the two services — see
 * {@code identity-service}'s own {@code IdentityEventEnvelope}, which carries
 * the full reasoning. The two records are kept in sync by hand; a field added
 * on the producer side that this module needs must be added here too.
 */
public record IdentityEventEnvelope(
        String eventType,
        Long userId,
        String email,
        String fullName,
        String token,
        Instant expiresAt,
        String reason) {
}
