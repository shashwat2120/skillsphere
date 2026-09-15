package com.skillsphere.realtime.internal;

import java.util.List;

/**
 * This service's own copy of the wire shape assessment-service publishes
 * to the {@code item-catalog-events} Kafka topic — see identity-service's
 * IdentityEventEnvelope for the reasoning behind a per-service copy rather
 * than a shared library.
 */
public record ItemCatalogChangedEvent(
        Long itemId,
        Long skillId,
        String stem,
        String status,
        List<OptionInfo> options) {

    public record OptionInfo(Long id, String text, boolean correct, int position) {
    }
}
