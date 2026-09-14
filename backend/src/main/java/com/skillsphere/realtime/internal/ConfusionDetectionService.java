package com.skillsphere.realtime.internal;

import com.skillsphere.realtime.domain.ConfusionSeverity;
import com.skillsphere.realtime.domain.ConfusionSignal;
import com.skillsphere.realtime.domain.ConfusionSignalRepository;
import com.skillsphere.skill.SkillLookup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The live half of "the system does not intervene, it tells the instructor
 * where to look" — watching for a run of consecutive wrong answers on the
 * same skill while an arena is in progress, the moment the alert is still
 * useful.
 *
 * <p><b>Per-instance streak counting, not a windowed SQL scan.</b> The
 * standing design ({@code confusion_signals.window_start}) anticipates
 * scanning stored responses for N failures inside a time window — real
 * infrastructure for a system with many concurrent instances and a large
 * response volume to sift. A live arena answers in strict, paced turns
 * already, so "N wrong in a row within this session" is the same signal
 * without the query: an in-memory counter keyed by (arena, learner), reset
 * on a correct answer. This does not survive a restart and does not span
 * multiple app instances — both true limitations, and both irrelevant at
 * the single-instance scale this deployment actually runs at. The
 * {@link ConfusionSignal} row it writes is the same durable record either
 * implementation would produce.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfusionDetectionService {

    @Value("${skillsphere.confusion.failure-threshold:3}")
    private int failureThreshold;

    private final ConfusionSignalRepository signals;
    private final SkillLookup skillLookup;
    private final SimpMessagingTemplate broker;

    private final Map<String, AtomicInteger> streaks = new ConcurrentHashMap<>();
    private final Map<String, Instant> streakStarted = new ConcurrentHashMap<>();

    public record ConfusionAlert(Long userId, String skillName, int failureCount, String severity) {
    }

    @Transactional
    public void recordAnswer(Long userId, Long skillId, boolean correct, Long arenaId) {
        String key = arenaId + ":" + userId;

        if (correct) {
            streaks.remove(key);
            streakStarted.remove(key);
            return;
        }

        AtomicInteger streak = streaks.computeIfAbsent(key, k -> new AtomicInteger(0));
        streakStarted.putIfAbsent(key, Instant.now());
        int count = streak.incrementAndGet();

        if (count >= failureThreshold) {
            raise(userId, skillId, count, streakStarted.get(key), arenaId);
            // Reset so the same streak doesn't fire again on every
            // subsequent wrong answer — the instructor has been told once;
            // a fresh streak (after a correct answer breaks this one) earns
            // a fresh alert.
            streaks.remove(key);
            streakStarted.remove(key);
        }
    }

    private void raise(Long userId, Long skillId, int failureCount, Instant windowStart, Long arenaId) {
        ConfusionSeverity severity = failureCount >= failureThreshold + 2
                ? ConfusionSeverity.HIGH
                : ConfusionSeverity.MEDIUM;

        ConfusionSignal signal = new ConfusionSignal(userId, skillId, failureCount, severity,
                windowStart == null ? Instant.now() : windowStart);
        signal.markNotified();
        signals.save(signal);

        String skillName = skillLookup.findById(skillId).map(SkillLookup.SkillInfo::name).orElse("this skill");
        ConfusionAlert alert = new ConfusionAlert(userId, skillName, failureCount, severity.name());
        broker.convertAndSend("/topic/arena/" + arenaId + "/confusion", alert);

        log.info("Confusion signal raised: user {} on '{}' — {} failures in a row (arena {})",
                userId, skillName, failureCount, arenaId);
    }
}
