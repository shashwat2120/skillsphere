package com.skillsphere.verification.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Keeps {@code skills_mirror} current — see that table's migration
 * comment (V2__skills_mirror.sql) for why this service keeps a local copy
 * instead of reading assessment-service's table live.
 *
 * <p>Upsert rather than insert: the same skill can be created once and
 * updated many times, and this consumer's job is "make the local row
 * match the latest event", not "record history" — {@code
 * skill_mastery_history} on assessment-service's own side is the append-
 * only ledger, this is a cache.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillCatalogEventListener {

    private final JdbcTemplate jdbc;

    @KafkaListener(topics = "skill-catalog-events", groupId = "verification-service")
    public void on(SkillCatalogChangedEvent event) {
        jdbc.update("""
                INSERT INTO skills_mirror (id, slug, name, level_band, active, updated_at)
                VALUES (?, ?, ?, ?, ?, now())
                ON CONFLICT (id) DO UPDATE SET
                    slug = EXCLUDED.slug,
                    name = EXCLUDED.name,
                    level_band = EXCLUDED.level_band,
                    active = EXCLUDED.active,
                    updated_at = now()
                """,
                event.id(), event.slug(), event.name(), event.levelBand(), event.active());

        log.debug("Mirrored skill catalog change for skill {} ({})", event.id(), event.name());
    }
}
