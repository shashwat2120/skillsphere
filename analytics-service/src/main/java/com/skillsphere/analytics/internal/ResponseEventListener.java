package com.skillsphere.analytics.internal;

import com.skillsphere.analytics.domain.DailyActivity;
import com.skillsphere.analytics.domain.DailyActivityId;
import com.skillsphere.analytics.domain.DailyActivityRepository;
import com.skillsphere.analytics.domain.LearningEvent;
import com.skillsphere.analytics.domain.LearningEventRepository;
import com.skillsphere.analytics.domain.LearningEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Turns an assessment response into the analytics spine's two durable
 * records: the append-only event, and today's incrementally-updated rollup.
 *
 * <p>Kafka, not {@code @ApplicationModuleListener} — assessment now runs in
 * a different process (the monolith), publishing to the
 * {@code assessment-events} topic via its own bridge listener rather than
 * an in-process event this service could ever have received directly. Same
 * reasoning as identity-service's KafkaEventBridge / the monolith's
 * IdentityMailListener: the durability and retry properties move from
 * Modulith's transactional outbox to Kafka's committed-offset semantics —
 * this consumer resumes exactly where it left off on restart rather than
 * losing or replaying events.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResponseEventListener {

    private final LearningEventRepository events;
    private final DailyActivityRepository dailyActivity;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "assessment-events", groupId = "analytics-service")
    public void on(ResponseRecordedEvent response) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("itemId", response.itemId());
        if (response.responseTimeMs() != null) {
            payload.put("responseTimeMs", response.responseTimeMs());
        }

        events.save(new LearningEvent(response.userId(), LearningEventType.ITEM_ANSWERED,
                "ITEM", response.itemId(), response.skillId(), response.correct(),
                payload.toString(), response.occurredAt()));

        LocalDate today = response.occurredAt().atZone(ZoneOffset.UTC).toLocalDate();
        DailyActivityId id = new DailyActivityId(response.userId(), today);
        DailyActivity activity = dailyActivity.findById(id).orElseGet(() -> new DailyActivity(id));
        activity.recordResponse(response.correct());
        dailyActivity.save(activity);

        log.debug("Ingested response event for user {} on skill {} (correct={})",
                response.userId(), response.skillId(), response.correct());
    }
}
