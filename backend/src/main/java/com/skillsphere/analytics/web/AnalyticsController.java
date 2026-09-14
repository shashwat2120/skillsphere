package com.skillsphere.analytics.web;

import com.skillsphere.analytics.domain.RiskScore;
import com.skillsphere.analytics.domain.RiskScoreRepository;
import com.skillsphere.analytics.internal.AnalyticsQueryService;
import com.skillsphere.analytics.internal.RiskScoringService;
import com.skillsphere.shared.error.NotFoundException;
import com.skillsphere.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The instructor dashboard's data source — who's active, who's struggling,
 * and why each flagged learner was flagged.
 *
 * <p>Under {@code /api/instructor/**}, which {@code SecurityConfig} already
 * restricts to {@code INSTRUCTOR}/{@code ADMIN} — no new security rule
 * needed, just landing in the path that already carries the right one.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Cohort summary and at-risk learner detection")
public class AnalyticsController {

    private final AnalyticsQueryService queryService;
    private final RiskScoringService riskScoringService;
    private final RiskScoreRepository riskScores;

    public record RiskScoreView(Long id, Long userId, double score, String band,
                                 String factors, boolean earlyWindow, boolean intervened, String computedAt) {
    }

    @GetMapping("/api/instructor/analytics/cohort")
    @Operation(summary = "Cohort summary — active learners, recent accuracy, how many are flagged")
    public AnalyticsQueryService.CohortSummary cohort() {
        return queryService.cohortSummary();
    }

    @GetMapping("/api/instructor/analytics/at-risk")
    @Operation(summary = "The at-risk queue",
            description = "Every learner currently in the MEDIUM or HIGH band, most recent computation "
                    + "only, sorted worst-first. Every entry carries the factors that produced it — the "
                    + "flag is never shown without its reasons.")
    public List<RiskScoreView> atRisk() {
        return queryService.atRiskQueue().stream().map(this::toView).toList();
    }

    @PostMapping("/api/instructor/analytics/recompute")
    @Operation(summary = "Recompute risk scores now",
            description = "The scheduled pass runs every 5 minutes on its own; this exists for a live "
                    + "demo or a dashboard's own refresh button, not because the schedule is unreliable.")
    public void recompute() {
        riskScoringService.recomputeAll();
    }

    @PostMapping("/api/instructor/analytics/at-risk/{riskScoreId}/acknowledge")
    @Operation(summary = "Mark a flagged learner as followed up on",
            description = "Records that an instructor acted on this specific computation. Known "
                    + "limitation: since scores are recomputed on a schedule and never overwritten, a "
                    + "learner still in a flagged band will produce a fresh, unacknowledged row on the "
                    + "next pass — this marks the record, it does not yet suppress the learner from "
                    + "reappearing once rescored.")
    public void acknowledge(@PathVariable Long riskScoreId) {
        RiskScore score = riskScores.findById(riskScoreId)
                .orElseThrow(() -> new NotFoundException("Risk score", riskScoreId));
        score.markIntervened(CurrentUser.requireId());
        riskScores.save(score);
    }

    private RiskScoreView toView(RiskScore r) {
        return new RiskScoreView(r.getId(), r.getUserId(), r.getScore().doubleValue(), r.getBand().name(),
                r.getFactors(), r.isEarlyWindow(), r.getIntervenedAt() != null, r.getComputedAt().toString());
    }
}
