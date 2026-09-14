package com.skillsphere.analytics.internal;

import com.skillsphere.assessment.ResponseRecorded;
import com.skillsphere.analytics.domain.DailyActivity;
import com.skillsphere.analytics.domain.DailyActivityId;
import com.skillsphere.analytics.domain.DailyActivityRepository;
import com.skillsphere.analytics.domain.LearningEvent;
import com.skillsphere.analytics.domain.LearningEventRepository;
import com.skillsphere.analytics.domain.LearningEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Turns an assessment response into the analytics spine's two durable
 * records: the append-only event, and today's incrementally-updated rollup.
 *
 * <p>{@link ApplicationModuleListener} gives this the same three properties
 * {@code IdentityMailListener} relies on: it only runs after the response
 * that produced it is actually committed, it runs off the request thread
 * (so a slow analytics write never makes answering a question feel slow),
 * and it is recorded in the outbox first, so a crash between commit and
 * this listener running loses nothing — it simply runs on restart.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResponseEventListener {

    private final LearningEventRepository events;
    private final DailyActivityRepository dailyActivity;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @ApplicationModuleListener
    public void on(ResponseRecorded response) {
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
