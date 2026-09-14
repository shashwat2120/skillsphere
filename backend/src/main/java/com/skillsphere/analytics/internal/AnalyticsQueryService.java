package com.skillsphere.analytics.internal;

import com.skillsphere.analytics.domain.LearningEventRepository;
import com.skillsphere.analytics.domain.RiskScore;
import com.skillsphere.analytics.domain.RiskScoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** The dashboard's read side: cohort summary and the at-risk queue. */
@Service
@RequiredArgsConstructor
public class AnalyticsQueryService {

    private final LearningEventRepository learningEvents;
    private final RiskScoreRepository riskScores;

    public record CohortSummary(int activeToday, int totalLearnersTracked, double recentAccuracy,
                                 int flaggedCount, int highRiskCount) {
    }

    @Transactional(readOnly = true)
    public CohortSummary cohortSummary() {
        Instant since24h = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant since7d = Instant.now().minus(7, ChronoUnit.DAYS);

        int activeToday = learningEvents.findActiveUserIds(since24h).size();
        int totalTracked = learningEvents.findAllUserIds().size();

        long answered = learningEvents.countByOccurredAtAfter(since7d);
        long correct = learningEvents.countByCorrectTrueAndOccurredAtAfter(since7d);
        double accuracy = answered == 0 ? 0.0 : (double) correct / answered;

        List<RiskScore> flagged = riskScores.findLatestFlagged();
        long highCount = flagged.stream().filter(r -> r.getBand().name().equals("HIGH")).count();

        return new CohortSummary(activeToday, totalTracked, round(accuracy), flagged.size(), (int) highCount);
    }

    @Transactional(readOnly = true)
    public List<RiskScore> atRiskQueue() {
        return riskScores.findLatestFlagged();
    }

    private double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
