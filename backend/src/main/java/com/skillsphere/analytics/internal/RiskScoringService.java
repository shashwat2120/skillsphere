package com.skillsphere.analytics.internal;

import com.skillsphere.analytics.domain.LearningEvent;
import com.skillsphere.analytics.domain.LearningEventRepository;
import com.skillsphere.analytics.domain.RiskBand;
import com.skillsphere.analytics.domain.RiskScore;
import com.skillsphere.analytics.domain.RiskScoreRepository;
import com.skillsphere.skill.SkillLookup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scores how likely a learner is to disengage, and states exactly why.
 *
 * <p><b>The three factors, and why these three.</b> Recent accuracy
 * (struggling), a failure cluster on one specific skill (stuck, not just
 * unlucky), and inactivity (drifting away) are the three signals this
 * project can actually compute honestly from what it has recorded — no
 * factor here is invented to look sophisticated. A true multi-week
 * "engagement slope" needs weeks of {@code daily_activity} history this
 * demo environment cannot have yet; the factors below are the subset of the
 * original design that real, single-session data can support without
 * pretending to a precision the inputs don't have.
 *
 * <p><b>The early-window boost</b> (score inflated when the learner is
 * within their first ~14 days) exists because roughly half of all dropouts
 * happen in the first two weeks — the same research finding that shaped
 * this product's scope in the first place. "Days since enrolment" is
 * approximated from this user's <em>first recorded learning event</em>
 * rather than their account creation date: analytics does not depend on
 * identity, and adding that dependency for one timestamp would be a real
 * module-boundary cost for a proxy that is already close enough to be
 * useful.
 *
 * <p>Scores are never overwritten — each run inserts a new row, so a
 * learner's risk history stays intact rather than only ever showing the
 * latest snapshot.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskScoringService {

    private static final int RECENT_RESPONSE_WINDOW = 15;
    private static final int MIN_RESPONSES_TO_SCORE = 3;
    private static final int CLUSTER_LOOKBACK = 5;
    private static final int CLUSTER_THRESHOLD = 3;

    @Value("${skillsphere.risk.early-window-days:14}")
    private int earlyWindowDays;

    private final LearningEventRepository learningEvents;
    private final RiskScoreRepository riskScores;
    private final SkillLookup skillLookup;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Recomputes risk for every learner with any recorded history. Runs
     * every 5 minutes — frequent enough that a live classroom session shows
     * a learner sliding into a risk band without waiting for an overnight
     * batch, which is the whole point of scoring this rather than a nightly
     * job the way a larger, slower-moving cohort might.
     */
    @Scheduled(fixedRate = 5, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    @Transactional
    public void recomputeAll() {
        List<Long> userIds = learningEvents.findAllUserIds();
        int scored = 0;
        for (Long userId : userIds) {
            if (scoreOne(userId) != null) {
                scored++;
            }
        }
        log.info("Risk scoring pass complete — {} of {} learners scored (rest below the evidence floor)",
                scored, userIds.size());
    }

    /** Same computation as the scheduled pass, for one learner, on demand. */
    @Transactional
    public RiskScore scoreOne(Long userId) {
        List<LearningEvent> recent = learningEvents.findRecentResponses(
                userId, PageRequest.of(0, RECENT_RESPONSE_WINDOW));
        if (recent.size() < MIN_RESPONSES_TO_SCORE) {
            // Not enough evidence to say anything responsible — silence
            // here is the honest answer, not a fabricated low-risk score.
            return null;
        }

        long correct = recent.stream().filter(LearningEvent::getCorrect).count();
        double recentAccuracy = (double) correct / recent.size();

        ClusterResult cluster = findFailureCluster(recent);

        Instant lastEvent = recent.get(0).getOccurredAt();
        long inactivityDays = Duration.between(lastEvent, Instant.now()).toDays();

        // The learner's true first-ever event, not merely the oldest one in
        // the recent window — the window is capped at
        // RECENT_RESPONSE_WINDOW responses and would understate tenure for
        // anyone with a longer history, undermining exactly the "first two
        // weeks matter most" signal this factor exists to capture.
        Instant firstEvent = learningEvents.findFirstByUserIdOrderByOccurredAtAsc(userId)
                .map(LearningEvent::getOccurredAt)
                .orElse(lastEvent);
        long daysSinceFirstSeen = Duration.between(firstEvent, Instant.now()).toDays();
        boolean earlyWindow = daysSinceFirstSeen <= earlyWindowDays;

        double accuracyPenalty = Math.max(0, 1 - recentAccuracy) * 0.40;
        double clusterPenalty = cluster.detected() ? 0.35 : 0.0;
        double inactivityPenalty = Math.min(1.0, inactivityDays / (double) earlyWindowDays) * 0.25;
        double raw = accuracyPenalty + clusterPenalty + inactivityPenalty;
        double score = Math.min(1.0, earlyWindow ? raw * 1.25 : raw);

        RiskBand band = score >= 0.66 ? RiskBand.HIGH : score >= 0.33 ? RiskBand.MEDIUM : RiskBand.LOW;

        String factors = buildFactors(recentAccuracy, cluster, inactivityDays, daysSinceFirstSeen, earlyWindow);

        RiskScore saved = riskScores.save(new RiskScore(userId,
                BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP), band, factors, earlyWindow));

        log.debug("Scored user {} — band={} score={} (accuracy={}, cluster={}, inactivityDays={})",
                userId, band, saved.getScore(), Math.round(recentAccuracy * 100), cluster.detected(), inactivityDays);

        return saved;
    }

    private record ClusterResult(boolean detected, Long skillId, int failureCount) {
    }

    /**
     * The most recent {@code CLUSTER_LOOKBACK} responses, checked for
     * {@code CLUSTER_THRESHOLD}+ wrong answers sharing one skill — "stuck on
     * this specific thing" rather than "having an unlucky streak in
     * general". Restricted to a short recent window rather than the whole
     * history, because a cluster from a month ago the learner has since
     * moved past is not a current reason for concern.
     */
    private ClusterResult findFailureCluster(List<LearningEvent> recent) {
        Map<Long, Integer> failuresBySkill = new LinkedHashMap<>();
        int lookback = Math.min(CLUSTER_LOOKBACK, recent.size());
        for (int i = 0; i < lookback; i++) {
            LearningEvent e = recent.get(i);
            if (Boolean.FALSE.equals(e.getCorrect()) && e.getSkillId() != null) {
                failuresBySkill.merge(e.getSkillId(), 1, Integer::sum);
            }
        }
        return failuresBySkill.entrySet().stream()
                .filter(entry -> entry.getValue() >= CLUSTER_THRESHOLD)
                .max(Map.Entry.comparingByValue())
                .map(entry -> new ClusterResult(true, entry.getKey(), entry.getValue()))
                .orElse(new ClusterResult(false, null, 0));
    }

    private String buildFactors(double recentAccuracy, ClusterResult cluster, long inactivityDays,
                                 long daysSinceFirstSeen, boolean earlyWindow) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("recentAccuracy", Math.round(recentAccuracy * 10000.0) / 10000.0);
        node.put("inactivityDays", inactivityDays);
        node.put("daysSinceFirstSeen", daysSinceFirstSeen);
        node.put("earlyWindow", earlyWindow);
        node.put("failureCluster", cluster.detected());
        if (cluster.detected()) {
            node.put("failureClusterSkillId", cluster.skillId());
            node.put("failureClusterSkillName",
                    skillLookup.findById(cluster.skillId()).map(SkillLookup.SkillInfo::name).orElse("Unknown skill"));
            node.put("failureClusterCount", cluster.failureCount());
        }
        return node.toString();
    }
}
