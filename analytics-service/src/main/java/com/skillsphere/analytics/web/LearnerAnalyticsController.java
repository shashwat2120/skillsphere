package com.skillsphere.analytics.web;

import com.skillsphere.analytics.internal.WhatIfSimulatorService;
import com.skillsphere.shared.error.ValidationException;
import com.skillsphere.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The learner-facing half of analytics — endpoints scoped to the caller's
 * own data under {@code /api/me/**}, as opposed to {@link AnalyticsController}'s
 * instructor-only cohort views. Kept as its own controller rather than
 * folded into that one so the two very different authorization stories —
 * INSTRUCTOR/ADMIN over the whole cohort here, any authenticated learner
 * over only their own record there — are never accidentally shared by one
 * class. {@code SecurityConfig}'s default-deny rule already covers this:
 * anything not matching {@code /api/instructor/**} just needs to be
 * authenticated, which is exactly this endpoint's requirement.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Learner-facing analytics: the what-if study simulator")
public class LearnerAnalyticsController {

    private final WhatIfSimulatorService whatIfSimulatorService;

    @GetMapping("/api/me/what-if")
    @Operation(summary = "Project six months of readiness at a given study pace",
            description = "A deterministic projection from this learner's own history and latest risk "
                    + "score, not a trained model — see WhatIfSimulatorService's class comment for the "
                    + "formula and why each input was chosen.")
    public List<WhatIfSimulatorService.MonthlyProjection> whatIf(@RequestParam double hoursPerWeek) {
        if (hoursPerWeek < 0 || hoursPerWeek > 100) {
            throw new ValidationException("INVALID_HOURS_PER_WEEK", "hoursPerWeek must be between 0 and 100.");
        }
        return whatIfSimulatorService.project(CurrentUser.requireId(), hoursPerWeek);
    }
}
