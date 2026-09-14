package com.skillsphere.career.internal;

import com.skillsphere.career.domain.ActivityType;
import com.skillsphere.career.internal.GapAnalysisService.GapAnalysis;
import com.skillsphere.career.internal.GapAnalysisService.SkillGap;
import com.skillsphere.skill.SkillLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a gap analysis into an ordered sequence of steps — the part of the
 * product that has to answer "why this, why now?" for every single one.
 *
 * <p><b>Ordering.</b> A topological sort restricted to the unmet skills
 * themselves: a step for skill B never appears before a step for skill A when
 * A hard-gates B, because teaching B first would be asking the learner to use
 * something they have not been shown yet. Ties — skills with no ordering
 * constraint between them — break toward whichever is furthest below its
 * target, on the theory that the biggest gap deserves attention soonest.
 * Prerequisites <em>outside</em> the requirement set are deliberately not
 * pulled in as their own steps: if a role needs Concurrency but not Generics,
 * inserting a Generics step would recommend work the learner does not need
 * for this goal. Its mastery is still looked up and shown in the rationale,
 * because "why this, why now" has to be honest about what it actually knows.
 *
 * <p><b>Rationale.</b> Written once, here, at generation time — never derived
 * later from whatever the learner's state happens to be when someone clicks
 * "why this?". Every prerequisite named in it carries a real, queried mastery
 * figure rather than an assumption: a prerequisite that is itself one of this
 * path's own earlier steps is labelled as such rather than claimed "met",
 * because at generation time it usually is not met yet — that would have been
 * a quietly false explanation on the very feature whose entire point is not
 * to make those.
 */
@Component
@RequiredArgsConstructor
public class PathGenerator {

    private final SkillLookup skillLookup;
    private final ObjectMapper objectMapper = new ObjectMapper();

    record GeneratedStep(Long skillId, ActivityType activityType, int estMinutes, String rationale) {
    }

    List<GeneratedStep> generate(GapAnalysis analysis, Long userId) {
        List<SkillGap> unmet = analysis.unmet();
        if (unmet.isEmpty()) {
            return List.of();
        }

        Set<Long> unmetIds = new LinkedHashSet<>();
        unmet.forEach(g -> unmetIds.add(g.skillId()));
        Map<Long, SkillGap> bySkill = new LinkedHashMap<>();
        unmet.forEach(g -> bySkill.put(g.skillId(), g));

        Map<Long, List<Long>> hardPrereqsBySkill = new LinkedHashMap<>();
        Set<Long> externalPrereqIds = new HashSet<>();
        for (Long skillId : unmetIds) {
            List<Long> prereqs = skillLookup.hardPrerequisitesOf(skillId);
            hardPrereqsBySkill.put(skillId, prereqs);
            for (Long p : prereqs) {
                if (!unmetIds.contains(p)) {
                    externalPrereqIds.add(p);
                }
            }
        }
        Map<Long, String> externalNames = skillLookup.namesOf(externalPrereqIds);
        Map<Long, SkillLookup.MasteryInfo> externalMastery = skillLookup.masteryOf(userId, externalPrereqIds);

        List<Long> ordered = topologicalOrder(unmetIds, hardPrereqsBySkill);

        List<GeneratedStep> steps = new ArrayList<>();
        for (Long skillId : ordered) {
            SkillGap gap = bySkill.get(skillId);
            String rationale = buildRationale(
                    gap, hardPrereqsBySkill.get(skillId), bySkill, externalNames, externalMastery, analysis);
            // Every step targets the adaptive diagnostic for its skill — the
            // one activity type Sprint 5 can actually serve end to end today.
            // Content is not yet tagged richly enough per skill to recommend a
            // specific lesson honestly, so this generator does not pretend to.
            steps.add(new GeneratedStep(skillId, ActivityType.ASSESSMENT, 45, rationale));
        }
        return steps;
    }

    /**
     * Kahn's algorithm restricted to the unmet skill set. Falls back to
     * insertion order (already weakest-gap-first, from {@link GapAnalysisService})
     * for any skill whose position isn't decided by an edge inside the set.
     */
    private List<Long> topologicalOrder(Set<Long> ids, Map<Long, List<Long>> hardPrereqsBySkill) {
        Map<Long, Set<Long>> dependents = new LinkedHashMap<>();
        Map<Long, Integer> inDegree = new LinkedHashMap<>();
        ids.forEach(id -> { dependents.put(id, new LinkedHashSet<>()); inDegree.put(id, 0); });

        for (Long id : ids) {
            for (Long prereq : hardPrereqsBySkill.get(id)) {
                if (ids.contains(prereq)) {
                    dependents.get(prereq).add(id);
                    inDegree.merge(id, 1, Integer::sum);
                }
            }
        }

        Deque<Long> ready = new ArrayDeque<>();
        for (Long id : ids) {
            if (inDegree.get(id) == 0) {
                ready.addLast(id);
            }
        }

        List<Long> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            Long next = ready.pollFirst();
            order.add(next);
            for (Long dependent : dependents.get(next)) {
                int remaining = inDegree.merge(dependent, -1, Integer::sum);
                if (remaining == 0) {
                    ready.addLast(dependent);
                }
            }
        }

        // A cycle among hard prerequisites should be impossible — the skill
        // graph rejects cycles on insert — but if the constraints somehow
        // didn't resolve everyone, append whatever is left in its original
        // order rather than silently dropping a required skill from the path.
        if (order.size() < ids.size()) {
            for (Long id : ids) {
                if (!order.contains(id)) {
                    order.add(id);
                }
            }
        }
        return order;
    }

    private String buildRationale(SkillGap gap, List<Long> hardPrereqIds, Map<Long, SkillGap> unmetBySkill,
                                   Map<Long, String> externalNames,
                                   Map<Long, SkillLookup.MasteryInfo> externalMastery,
                                   GapAnalysis analysis) {
        ObjectNode root = objectMapper.createObjectNode();

        ArrayNode prereqs = root.putArray("prerequisites");
        for (Long prereqId : hardPrereqIds) {
            ObjectNode p = prereqs.addObject();
            p.put("skillId", prereqId);
            SkillGap peer = unmetBySkill.get(prereqId);
            if (peer != null) {
                // A peer step in this same path, not yet met at generation
                // time — say so rather than claim it is satisfied.
                p.put("name", peer.skillName());
                p.put("status", "earlier_in_path");
            } else {
                SkillLookup.MasteryInfo m = externalMastery.get(prereqId);
                p.put("name", externalNames.getOrDefault(prereqId, "Unknown skill"));
                p.put("mastery", m != null ? round(m.masteryProbability()) : 0.0);
                p.put("status", m != null && m.masteryProbability() > 0 ? "satisfied" : "not_yet_attempted");
            }
        }

        ObjectNode gapNode = root.putObject("gap");
        gapNode.put("current", round(gap.currentMastery()));
        gapNode.put("required", gap.requiredMastery());

        root.put("roleWeight", gap.weight());
        root.put("core", gap.core());
        root.put("selectedBecause", selectedBecause(gap, analysis));

        return root.toString();
    }

    private String selectedBecause(SkillGap gap, GapAnalysis analysis) {
        String coreNote = gap.core()
                ? "a core requirement for " + analysis.role().getTitle()
                : "a weighted requirement for " + analysis.role().getTitle();
        return "%s is below the mastery %s needs (%.0f%% of %.0f%% required) and is %s."
                .formatted(gap.skillName(), analysis.role().getTitle(),
                        gap.currentMastery() * 100, gap.requiredMastery() * 100, coreNote);
    }

    private double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
