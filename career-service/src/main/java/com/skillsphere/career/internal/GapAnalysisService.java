package com.skillsphere.career.internal;

import com.skillsphere.career.domain.CareerRole;
import com.skillsphere.career.domain.RoleSkillRequirement;
import com.skillsphere.career.domain.RoleSkillRequirementRepository;
import com.skillsphere.shared.error.ValidationException;
import com.skillsphere.skill.SkillLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Compares a learner's current skill profile against a role's target vector.
 *
 * <p>Two numbers come out of this, and they answer different questions.
 * {@code readinessScore} is a continuous weighted average across every
 * requirement, core and non-core alike — useful for showing progress moving.
 * {@code readyForRole} is a hard boolean: every <em>core</em> requirement met,
 * nothing more, nothing less. A learner at 40% on one core skill and 100% on
 * every non-core one is not ready, no matter how high the average looks —
 * V6's own comment on the schema says as much: "core skills gate readiness;
 * non-core skills raise the score but never block it." Reporting only the
 * average would let a rounded-up number claim a readiness the learner has not
 * actually earned.
 */
@Service
@RequiredArgsConstructor
public class GapAnalysisService {

    private final RoleSkillRequirementRepository requirements;
    private final SkillLookup skillLookup;

    record SkillGap(
            Long skillId,
            String skillName,
            double currentMastery,
            double requiredMastery,
            double weight,
            boolean core,
            boolean met) {
    }

    record GapAnalysis(
            CareerRole role,
            List<SkillGap> gaps,
            double readinessScore,
            boolean readyForRole) {

        List<SkillGap> unmet() {
            return gaps.stream().filter(g -> !g.met()).toList();
        }
    }

    @Transactional(readOnly = true)
    GapAnalysis analyze(Long userId, CareerRole role) {
        List<RoleSkillRequirement> reqs = requirements.findByCareerRoleId(role.getId());
        if (reqs.isEmpty()) {
            throw new ValidationException("ROLE_NOT_CONFIGURED",
                    role.getTitle() + " has no skill requirements defined yet.");
        }

        List<Long> skillIds = reqs.stream().map(RoleSkillRequirement::getSkillId).toList();
        Map<Long, String> names = skillLookup.namesOf(skillIds);
        Map<Long, SkillLookup.MasteryInfo> mastery = skillLookup.masteryOf(userId, skillIds);

        List<SkillGap> gaps = reqs.stream()
                .map(req -> {
                    double current = mastery.containsKey(req.getSkillId())
                            ? mastery.get(req.getSkillId()).masteryProbability()
                            : 0.0;
                    double required = req.getRequiredMastery().doubleValue();
                    return new SkillGap(
                            req.getSkillId(),
                            names.getOrDefault(req.getSkillId(), "Unknown skill"),
                            current, required,
                            req.getWeight().doubleValue(),
                            req.isCore(),
                            current >= required);
                })
                // Weakest relative to target first — what the learner should look at first.
                .sorted(Comparator.comparingDouble(g -> g.currentMastery() / g.requiredMastery()))
                .toList();

        double weightSum = gaps.stream().mapToDouble(SkillGap::weight).sum();
        double weightedProgress = gaps.stream()
                .mapToDouble(g -> g.weight() * Math.min(g.currentMastery() / g.requiredMastery(), 1.0))
                .sum();
        double readiness = weightSum > 0 ? (weightedProgress / weightSum) * 100.0 : 0.0;

        boolean ready = gaps.stream().filter(SkillGap::core).allMatch(SkillGap::met);

        return new GapAnalysis(role, gaps, round(readiness), ready);
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
