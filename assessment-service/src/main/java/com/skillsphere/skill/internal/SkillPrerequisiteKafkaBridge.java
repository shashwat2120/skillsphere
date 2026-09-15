package com.skillsphere.skill.internal;

import com.skillsphere.skill.SkillPrerequisiteChanged;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Where an in-process prerequisite-edge fact becomes a message career-
 * service (the one consumer that mirrors the graph's edges, not just its
 * nodes) can see.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillPrerequisiteKafkaBridge {

    private static final String TOPIC = "skill-prerequisite-events";

    private final KafkaTemplate<String, SkillPrerequisiteChanged> kafka;

    @ApplicationModuleListener
    void on(SkillPrerequisiteChanged event) {
        log.debug("Publishing prerequisite change {} -> {} (removed={}) to Kafka",
                event.prerequisiteSkillId(), event.skillId(), event.removed());
        kafka.send(TOPIC, String.valueOf(event.skillId()), event);
    }
}
