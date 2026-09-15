package com.skillsphere.shared.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Where an in-process audit fact becomes a message identity-service can see.
 *
 * <p>Same shape as {@code AnalyticsEventBridge} and identity-service's own
 * {@code KafkaEventBridge}: {@link AuditLogger} still just calls {@code
 * events.publishEvent(...)}, still inside Modulith's transactional outbox;
 * this is the only new component, and its entire job is putting the fact on
 * Kafka.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAuditKafkaBridge {

    private static final String TOPIC = "admin-audit-events";

    private final KafkaTemplate<String, AdminActionRecorded> kafka;

    @ApplicationModuleListener
    void on(AdminActionRecorded event) {
        log.debug("Publishing admin audit event {} on {} {} to Kafka", event.action(), event.targetType(), event.targetId());
        // Keyed by admin id for the same ordering reason every other bridge
        // in this codebase keys by the relevant actor.
        kafka.send(TOPIC, String.valueOf(event.adminId()), event);
    }
}
