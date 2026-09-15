package com.skillsphere.career.internal;

import com.skillsphere.career.domain.CareerRole;
import com.skillsphere.career.domain.CareerRoleRepository;
import com.skillsphere.career.domain.GenerationReason;
import com.skillsphere.career.domain.LearnerCareerGoal;
import com.skillsphere.career.domain.LearnerCareerGoalRepository;
import com.skillsphere.career.domain.LearningPath;
import com.skillsphere.career.domain.LearningPathRepository;
import com.skillsphere.career.domain.PathStatus;
import com.skillsphere.career.domain.PathStep;
import com.skillsphere.career.domain.PathStepRepository;
import com.skillsphere.career.domain.StepStatus;
import com.skillsphere.career.internal.GapAnalysisService.GapAnalysis;
import com.skillsphere.career.internal.PathGenerator.GeneratedStep;
import com.skillsphere.shared.error.NotFoundException;
import com.skillsphere.skill.SkillLookup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The learner-facing half of the module: browse roles, set a goal, get the
 * path that follows from it.
 *
 * <p><b>At most one primary goal per learner</b> is a database invariant
 * ({@code uq_lcg_one_primary}), not just an application assumption — the same
 * shape as the diagnostic-assessment bug fixed earlier this session, where an
 * {@code Optional}-returning finder quietly assumed a uniqueness the schema
 * didn't yet enforce. Here the constraint already existed from the start, so
 * {@link #setGoal} demotes any existing primary goal in the same transaction
 * it promotes the new one — sequential writes inside one transaction, not two
 * requests racing each other, so there is no equivalent window to close.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CareerService {

    private final CareerRoleRepository roles;
    private final LearnerCareerGoalRepository goals;
    private final LearningPathRepository paths;
    private final PathStepRepository pathSteps;
    private final GapAnalysisService gapAnalysis;
    private final PathGenerator pathGenerator;
    private final SkillLookup skillLookup;

    public record RoleSummary(Long id, String title, String slug, String description,
                        String category, String seniority, String icon) {
    }

    public record PathStepView(Long id, Long skillId, String skillName, String activityType,
                         String status, int estMinutes, String rationale) {
    }

    public record PathView(Long id, int version, String status, String generationReason,
                     int totalSteps, int completedSteps, List<PathStepView> steps) {
    }

    public record GapView(String skillName, double currentMastery, double requiredMastery,
                    double weight, boolean core, boolean met) {
    }

    public record GapAnalysisView(Long roleId, String roleTitle, double readinessScore,
                            boolean readyForRole, List<GapView> gaps) {
    }

    @Transactional(readOnly = true)
    public List<RoleSummary> catalogue() {
        return roles.findByActiveTrueOrderByTitleAsc().stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public GapAnalysisView analyzeGap(Long userId, Long roleId) {
        CareerRole role = requireRole(roleId);
        GapAnalysis analysis = gapAnalysis.analyze(userId, role);
        return toGapView(analysis);
    }

    /**
     * Sets a role as the learner's primary goal and (re)generates the path
     * toward it. Idempotent in effect: calling this again for the same role
     * simply supersedes the previous path with a fresh one, which is exactly
     * what should happen if the learner's evidence changed since the last
     * time they looked.
     */
    @Transactional
    public PathView setGoal(Long userId, Long roleId) {
        CareerRole role = requireRole(roleId);

        goals.findByUserIdAndPrimaryTrue(userId).ifPresent(existing -> {
            if (!existing.getCareerRoleId().equals(roleId)) {
                existing.setPrimary(false);
                goals.save(existing);
            }
        });

        LearnerCareerGoal goal = goals.findByUserIdAndCareerRoleId(userId, roleId)
                .orElseGet(() -> new LearnerCareerGoal(userId, roleId));
        goal.setPrimary(true);
        goals.save(goal);

        GenerationReason reason = paths.findByUserIdAndCareerRoleIdAndStatus(userId, roleId, PathStatus.ACTIVE)
                .isPresent() ? GenerationReason.GOAL_CHANGED : GenerationReason.INITIAL;

        return regeneratePath(userId, role, reason);
    }

    @Transactional(readOnly = true)
    public Optional<PathView> activePath(Long userId) {
        return goals.findByUserIdAndPrimaryTrue(userId)
                .flatMap(goal -> paths.findByUserIdAndCareerRoleIdAndStatus(
                        userId, goal.getCareerRoleId(), PathStatus.ACTIVE))
                .map(this::toPathView);
    }

    private PathView regeneratePath(Long userId, CareerRole role, GenerationReason reason) {
        int nextVersion = paths.findByUserIdAndCareerRoleIdAndStatus(userId, role.getId(), PathStatus.ACTIVE)
                .map(existing -> {
                    existing.supersede();
                    paths.save(existing);
                    return existing.getVersion() + 1;
                })
                .orElse(1);

        GapAnalysis analysis = gapAnalysis.analyze(userId, role);
        List<GeneratedStep> generated = pathGenerator.generate(analysis, userId);

        LearningPath path = new LearningPath(userId, role.getId(), nextVersion, reason);
        path.setTotalSteps(generated.size());
        path = paths.save(path);

        int position = 0;
        List<PathStep> steps = new ArrayList<>();
        for (GeneratedStep g : generated) {
            PathStep step = new PathStep(path.getId(), position++, g.skillId(),
                    g.activityType(), g.estMinutes(), g.rationale());
            // The first step (and only the first — later steps unlock as their
            // predecessor completes, once that wiring exists) starts available
            // rather than locked, so a freshly generated path is never a wall
            // of locked cards with nothing to click.
            if (position == 1) {
                step.setStatus(StepStatus.AVAILABLE);
            }
            steps.add(step);
        }
        pathSteps.saveAll(steps);

        log.info("Generated learning path {} v{} for user {} toward '{}' — {} steps ({})",
                path.getId(), path.getVersion(), userId, role.getTitle(), steps.size(), reason);

        return toPathView(path, steps);
    }

    private PathView toPathView(LearningPath path) {
        List<PathStep> steps = pathSteps.findByLearningPathIdOrderByPosition(path.getId());
        return toPathView(path, steps);
    }

    private PathView toPathView(LearningPath path, List<PathStep> steps) {
        Map<Long, String> names = skillLookup.namesOf(steps.stream().map(PathStep::getSkillId).toList());

        List<PathStepView> stepViews = steps.stream()
                .map(s -> new PathStepView(
                        s.getId(), s.getSkillId(), names.get(s.getSkillId()),
                        s.getActivityType().name(), s.getStatus().name(),
                        s.getEstMinutes(), s.getRationale()))
                .toList();

        return new PathView(path.getId(), path.getVersion(), path.getStatus().name(),
                path.getGenerationReason().name(), path.getTotalSteps(), path.getCompletedSteps(), stepViews);
    }

    private GapAnalysisView toGapView(GapAnalysis analysis) {
        List<GapView> gapViews = analysis.gaps().stream()
                .map(g -> new GapView(g.skillName(), g.currentMastery(), g.requiredMastery(),
                        g.weight(), g.core(), g.met()))
                .toList();
        return new GapAnalysisView(analysis.role().getId(), analysis.role().getTitle(),
                analysis.readinessScore(), analysis.readyForRole(), gapViews);
    }

    private CareerRole requireRole(Long roleId) {
        return roles.findById(roleId)
                .filter(CareerRole::isActive)
                .orElseThrow(() -> new NotFoundException("Career role", roleId));
    }

    private RoleSummary toSummary(CareerRole role) {
        return new RoleSummary(role.getId(), role.getTitle(), role.getSlug(), role.getDescription(),
                role.getCategory(), role.getSeniority().name(), role.getIcon());
    }
}
