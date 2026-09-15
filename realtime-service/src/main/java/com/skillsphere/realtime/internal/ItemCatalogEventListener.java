package com.skillsphere.realtime.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps {@code items_mirror}/{@code item_options_mirror} current — see
 * V3__items_mirror.sql.
 *
 * <p>Options are replaced wholesale on every event rather than diffed,
 * matching how rarely this actually fires (activate/retire only — see
 * {@code ItemCatalogChanged}'s own class comment) and how cheap a handful
 * of option rows are to rewrite compared to the bookkeeping a diff would
 * need.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItemCatalogEventListener {

    private final JdbcTemplate jdbc;

    @KafkaListener(topics = "item-catalog-events", groupId = "realtime-service",
            containerFactory = "itemCatalogListenerContainerFactory")
    @Transactional
    public void on(ItemCatalogChangedEvent event) {
        jdbc.update("""
                INSERT INTO items_mirror (id, skill_id, stem, status, updated_at)
                VALUES (?, ?, ?, ?, now())
                ON CONFLICT (id) DO UPDATE SET
                    skill_id = EXCLUDED.skill_id,
                    stem = EXCLUDED.stem,
                    status = EXCLUDED.status,
                    updated_at = now()
                """,
                event.itemId(), event.skillId(), event.stem(), event.status());

        jdbc.update("DELETE FROM item_options_mirror WHERE item_id = ?", event.itemId());
        for (ItemCatalogChangedEvent.OptionInfo option : event.options()) {
            jdbc.update("""
                    INSERT INTO item_options_mirror (id, item_id, text, correct, position)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    option.id(), event.itemId(), option.text(), option.correct(), option.position());
        }

        log.debug("Mirrored item catalog change for item {} (status={}, {} options)",
                event.itemId(), event.status(), event.options().size());
    }
}
