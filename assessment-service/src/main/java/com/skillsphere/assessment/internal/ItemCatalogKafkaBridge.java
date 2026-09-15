package com.skillsphere.assessment.internal;

import com.skillsphere.assessment.ItemCatalogChanged;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Where an in-process item-catalog fact becomes a message realtime-service
 * (the only consumer — its local arena item mirror) can see. Same shape as
 * {@link AnalyticsEventBridge}: {@link ItemBankService} still just calls
 * {@code events.publishEvent(...)}, still inside Modulith's transactional
 * outbox.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItemCatalogKafkaBridge {

    private static final String TOPIC = "item-catalog-events";

    private final KafkaTemplate<String, ItemCatalogChanged> kafka;

    @ApplicationModuleListener
    void on(ItemCatalogChanged event) {
        log.debug("Publishing item catalog change for item {} (status={}) to Kafka",
                event.itemId(), event.status());
        kafka.send(TOPIC, String.valueOf(event.itemId()), event);
    }
}
