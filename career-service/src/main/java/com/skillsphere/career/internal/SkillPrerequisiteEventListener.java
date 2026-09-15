package com.skillsphere.career.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Keeps {@code skill_prerequisites_mirror} current — see
 * V3__skill_prerequisites_mirror.sql.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillPrerequisiteEventListener {

    private final JdbcTemplate jdbc;

    @KafkaListener(topics = "skill-prerequisite-events", groupId = "career-service",
            containerFactory = "skillPrerequisiteListenerContainerFactory")
    public void on(SkillPrerequisiteChangedEvent event) {
        if (event.removed()) {
            jdbc.update("DELETE FROM skill_prerequisites_mirror WHERE skill_id = ? AND prerequisite_skill_id = ?",
                    event.skillId(), event.prerequisiteSkillId());
        } else {
            jdbc.update("""
                    INSERT INTO skill_prerequisites_mirror (skill_id, prerequisite_skill_id, strength, updated_at)
                    VALUES (?, ?, ?, now())
                    ON CONFLICT (skill_id, prerequisite_skill_id) DO UPDATE SET
                        strength = EXCLUDED.strength,
                        updated_at = now()
                    """,
                    event.skillId(), event.prerequisiteSkillId(), event.strength());
        }

        log.debug("Mirrored prerequisite change {} -> {} (removed={})",
                event.prerequisiteSkillId(), event.skillId(), event.removed());
    }
}
