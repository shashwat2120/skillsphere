package com.skillsphere.assessment;

import java.time.Instant;

/**
 * Published every time a learner answers a diagnostic item.
 *
 * <p>Lives in assessment's root package — its public API, alongside
 * {@link ArenaItemSource} — rather than a separate {@code events} named
 * interface, because this is currently the module's only event and a
 * dedicated subpackage would be structure with nothing to organise yet.
 *
 * <p>Assessment states a fact — this response happened, on this skill, with
 * this outcome — and has no opinion about who reads it. Analytics is the
 * first consumer (rolling up daily activity, scoring risk), but nothing
 * about this event is analytics-specific; it is exactly what an
 * event-sourced spine is supposed to look like; the same event becomes a
 * Kafka message in Sprint 6 without this record changing at all.
 *
 * <p>Written to the outbox in the same transaction as the response it
 * describes ({@code ApplicationModuleListener} on the consuming side), so a
 * response that gets rolled back never produces an event for work that
 * didn't happen.
 */
public record ResponseRecorded(
        Long userId,
        Long skillId,
        Long itemId,
        boolean correct,
        Integer responseTimeMs,
        Instant occurredAt) {
}
