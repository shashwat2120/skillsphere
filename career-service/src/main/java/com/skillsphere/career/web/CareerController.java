package com.skillsphere.career.web;

import com.skillsphere.career.internal.CareerService;
import com.skillsphere.shared.error.NotFoundException;
import com.skillsphere.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Career goals, gap analysis, and the generated path toward a goal.
 *
 * <p>Three reads and one write, because that is the whole feature: browse
 * roles, see the gap to one of them, commit to one as a goal (which is also
 * the trigger that generates a path), and read back the active path. The
 * "why this?" panel needs nothing of its own — {@code rationale} rides along
 * on every step in {@code getActivePath}, because it was written once, at
 * generation time, and is never recomputed for display.
 */
@RestController
@RequestMapping("/api/careers")
@RequiredArgsConstructor
@Tag(name = "Career & Path", description = "Career catalogue, gap analysis and generated learning paths")
public class CareerController {

    private final CareerService careerService;

    @GetMapping
    @Operation(summary = "List active career roles")
    public java.util.List<CareerService.RoleSummary> catalogue() {
        return careerService.catalogue();
    }

    @GetMapping("/{roleId}/gap")
    @Operation(summary = "Gap analysis against one role",
            description = "Read-only — does not set a goal or touch any path. For browsing roles before "
                    + "committing to one.")
    public CareerService.GapAnalysisView gap(@PathVariable Long roleId) {
        return careerService.analyzeGap(CurrentUser.requireId(), roleId);
    }

    @PostMapping("/{roleId}/goal")
    @Operation(summary = "Set a role as the learner's primary goal",
            description = "Generates a fresh path toward the role. Calling this again for the same role "
                    + "regenerates the path against current evidence rather than erroring; calling it for a "
                    + "different role replaces the previous primary goal.")
    public CareerService.PathView setGoal(@PathVariable Long roleId) {
        return careerService.setGoal(CurrentUser.requireId(), roleId);
    }

    @GetMapping("/path")
    @Operation(summary = "The learner's active path",
            description = "404 if no goal has been set yet — there is nothing generated to return.")
    public CareerService.PathView activePath() {
        return careerService.activePath(CurrentUser.requireId())
                .orElseThrow(() -> new NotFoundException("No active learning path — set a career goal first."));
    }
}
