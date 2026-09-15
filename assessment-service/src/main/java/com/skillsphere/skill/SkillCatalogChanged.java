package com.skillsphere.skill;

/**
 * Published whenever a skill's catalog-facing state changes — created,
 * updated, or retired (retirement is just {@code active=false}, matching
 * {@link SkillService#retire} never deleting a row).
 *
 * <p>This is the read-model half of the database-per-service split: content,
 * career, verification, realtime and analytics-service each keep a small
 * local mirror of {@code skills} — the exact fields {@link SkillLookup
 * .SkillInfo} already exposed when everyone read the shared table directly
 * — rather than a synchronous call on every lookup. Skills change on the
 * order of once a semester; a call to assessment-service for every "what is
 * skill 3 called" lookup would trade a rare write for a permanent runtime
 * dependency on every one of those five services' hot read paths. Each
 * mirror starts from the same static seed data this table itself does
 * (V899 in the original monolith), so a fresh clone needs no backfill —
 * only future deltas need to travel over Kafka at all.
 *
 * <p>Lives in {@code skill}'s root package, the module's public API,
 * alongside {@link SkillLookup} — the interface it is functionally an
 * asynchronous cousin of.
 */
public record SkillCatalogChanged(
        Long id,
        String slug,
        String name,
        String levelBand,
        boolean active) {
}
