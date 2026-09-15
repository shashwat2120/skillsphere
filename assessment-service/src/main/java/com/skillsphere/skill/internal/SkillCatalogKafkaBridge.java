package com.skillsphere.skill.internal;

import com.skillsphere.skill.SkillCatalogChanged;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Where an in-process skill-catalog fact becomes a message the five
 * services that mirror it can see. Same shape as every other Kafka bridge
 * in this codebase: {@link SkillService} still just calls {@code
 * events.publishEvent(...)}, still inside Modulith's transactional outbox.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillCatalogKafkaBridge {

    private static final String TOPIC = "skill-catalog-events";

    private final KafkaTemplate<String, SkillCatalogChanged> kafka;

    @ApplicationModuleListener
    void on(SkillCatalogChanged event) {
        log.debug("Publishing skill catalog change for skill {} to Kafka", event.id());
        kafka.send(TOPIC, String.valueOf(event.id()), event);
    }
}
