package com.skillsphere.verification.internal;

/**
 * This service's own copy of the wire shape assessment-service publishes
 * to the {@code skill-catalog-events} Kafka topic — the field-for-field
 * twin of assessment's in-process {@code SkillCatalogChanged} record,
 * deliberately not a shared library between the two services. See
 * identity-service's IdentityEventEnvelope for the full reasoning.
 */
public record SkillCatalogChangedEvent(
        Long id,
        String slug,
        String name,
        String levelBand,
        boolean active) {
}
