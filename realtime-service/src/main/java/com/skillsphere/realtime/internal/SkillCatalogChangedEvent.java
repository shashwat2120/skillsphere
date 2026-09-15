package com.skillsphere.realtime.internal;

/**
 * This service's own copy of the wire shape assessment-service publishes
 * to the {@code skill-catalog-events} Kafka topic — see identity-service's
 * IdentityEventEnvelope for the reasoning behind a per-service copy rather
 * than a shared library.
 */
public record SkillCatalogChangedEvent(
        Long id,
        String slug,
        String name,
        String levelBand,
        boolean active) {
}
