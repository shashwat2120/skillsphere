package com.skillsphere.career.internal;

/**
 * This service's own copy of the wire shape assessment-service publishes
 * to the {@code skill-prerequisite-events} Kafka topic — see identity-
 * service's IdentityEventEnvelope for the reasoning behind a per-service
 * copy rather than a shared library.
 */
public record SkillPrerequisiteChangedEvent(
        Long skillId,
        Long prerequisiteSkillId,
        double strength,
        boolean removed) {
}
