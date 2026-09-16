package com.skillsphere.analytics.internal;

import com.skillsphere.analytics.domain.LearningEvent;
import com.skillsphere.analytics.domain.LearningEventRepository;
import com.skillsphere.analytics.domain.LearningEventType;
import com.skillsphere.analytics.domain.RiskScore;
import com.skillsphere.analytics.domain.RiskScoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * The what-if simulator: "if I study N hours a week, where am I in six
 * months?" — a deterministic, explainable projection, not a model trained
 * on outcomes this project has no data to train on.
 *
 * <p><b>The formula.</b> {@code readiness(m) = 1 - (1 - baseline) *
 * exp(-k * cumulativeItems(m))}, where {@code cumulativeItems(m)} is the
 * learner's projected items answered through month {@code m} at their
 * chosen study pace. This is a capped-growth curve on purpose, not a
 * straight line: the first study sessions close more of the readiness gap
 * than the next few hundred do — the same diminishing-returns shape real
 * mastery curves have — and it can never overshoot 100% the way a linear
 * projection eventually would however far it's extrapolated.
 *
 * <p><b>Where the inputs come from.</b> {@code baseline} is
 * {@code 1 - latestRiskScore} — this learner's own most recent computed
 * risk, inverted, so the projection starts from where they actually are
 * rather than a generic starting point; a learner {@link RiskScoringService}
 * has never scored starts from a deliberately conservative 20%. Velocity
 * (correct answers per hour of study) comes from this learner's own
 * history: total correct {@code ITEM_ANSWERED} events divided by total time
 * spent answering, read from each event's recorded {@code responseTimeMs}
 * payload field. A learner with no history yet — or too little recorded
 * response time to trust as a rate — falls back to 3 items/hour, a
 * plausible pace for this project's item bank, not a measured one, used
 * only until real evidence exists.
 *
 * <p><b>The constant k.</b> Chosen, not fitted — there is no outcome data
 * in this demo to fit it to — so that a learner studying a few hours a week
 * at the default pace visibly closes most of the gap to 100% over six
 * months without saturating in the first few weeks, which is the shape an
 * instructor or learner would find credible on sight.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatIfSimulatorService {

    private static final double DEFAULT_ITEMS_PER_HOUR = 3.0;
    private static final double DEFAULT_BASELINE_READINESS = 0.20;
    private static final double MIN_TRUSTED_HOURS = 0.05;
    private static final double WEEKS_PER_MONTH = 4.345;
    private static final double GROWTH_CONSTANT = 0.01;
    private static final int PROJECTION_MONTHS = 6;

    private final LearningEventRepository learningEvents;
    private final RiskScoreRepository riskScores;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public record MonthlyProjection(int month, double projectedReadiness) {
    }

    @Transactional(readOnly = true)
    public List<MonthlyProjection> project(Long userId, double hoursPerWeek) {
        double itemsPerHour = estimateItemsPerHour(userId);
        double baseline = estimateBaselineReadiness(userId);
        double itemsPerWeek = itemsPerHour * hoursPerWeek;

        List<MonthlyProjection> projection = new ArrayList<>(PROJECTION_MONTHS);
        for (int month = 1; month <= PROJECTION_MONTHS; month++) {
            double cumulativeItems = itemsPerWeek * WEEKS_PER_MONTH * month;
            double readiness = 1 - (1 - baseline) * Math.exp(-GROWTH_CONSTANT * cumulativeItems);
            projection.add(new MonthlyProjection(month, round(clamp01(readiness))));
        }

        log.debug("What-if projection for user {} at {} h/week — {} items/hour, baseline {}",
                userId, hoursPerWeek, Math.round(itemsPerHour * 100) / 100.0, round(baseline));

        return projection;
    }

    private double estimateItemsPerHour(Long userId) {
        List<LearningEvent> history = learningEvents.findByUserIdAndEventType(userId, LearningEventType.ITEM_ANSWERED);
        if (history.isEmpty()) {
            return DEFAULT_ITEMS_PER_HOUR;
        }

        long correct = 0;
        long totalMs = 0;
        for (LearningEvent event : history) {
            if (Boolean.TRUE.equals(event.getCorrect())) {
                correct++;
            }
            totalMs += responseTimeMs(event);
        }

        double hours = totalMs / 3_600_000.0;
        if (hours < MIN_TRUSTED_HOURS) {
            // Too little recorded response time to trust as a rate — divide
            // by a near-zero denominator here and a handful of fast guesses
            // would extrapolate into an absurd items/hour figure.
            return DEFAULT_ITEMS_PER_HOUR;
        }
        return correct / hours;
    }

    private long responseTimeMs(LearningEvent event) {
        try {
            JsonNode node = objectMapper.readTree(event.getPayload());
            JsonNode responseTime = node.get("responseTimeMs");
            return responseTime != null && responseTime.isNumber() ? responseTime.asLong() : 0L;
        } catch (RuntimeException e) {
            // Malformed or missing payload on one row shouldn't sink the
            // whole estimate — just treat that response as untimed.
            return 0L;
        }
    }

    private double estimateBaselineReadiness(Long userId) {
        return riskScores.findFirstByUserIdOrderByComputedAtDesc(userId)
                .map(RiskScore::getScore)
                .map(score -> 1.0 - score.doubleValue())
                .orElse(DEFAULT_BASELINE_READINESS);
    }

    private double clamp01(double v) {
        return Math.min(1.0, Math.max(0.0, v));
    }

    private double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
