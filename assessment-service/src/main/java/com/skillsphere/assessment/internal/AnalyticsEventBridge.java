package com.skillsphere.assessment.internal;

import com.skillsphere.assessment.ResponseRecorded;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Where an in-process fact becomes a message another process can see.
 *
 * <p>{@link DiagnosticService} is completely unchanged — it still just calls
 * {@code events.publishEvent(new ResponseRecorded(...))}, still inside
 * Modulith's transactional outbox. This is the only new thing: one more
 * listener on that same local event, whose entire job is to put the fact on
 * Kafka so analytics-service (a different process now) can react to it.
 * Same pattern as identity-service's KafkaEventBridge, and for the same
 * reason — {@code ResponseRecorded}'s own doc comment already said this
 * event "becomes a Kafka message in Sprint 6 without this record changing
 * at all," and that is exactly what happens here: the record crosses
 * unchanged, this bridge is the only new code.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsEventBridge {

    private static final String TOPIC = "assessment-events";

    private final KafkaTemplate<String, ResponseRecorded> kafka;

    @ApplicationModuleListener
    void on(ResponseRecorded event) {
        log.debug("Publishing response event for user {} to Kafka", event.userId());
        // Keyed by user id for the same reason identity's bridge is: Kafka's
        // partitioning then keeps one learner's events in order relative to
        // each other, which in-process synchronous delivery gave for free.
        kafka.send(TOPIC, String.valueOf(event.userId()), event);
    }
}
