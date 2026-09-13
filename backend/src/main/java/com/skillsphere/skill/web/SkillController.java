package com.skillsphere.skill.web;

import com.skillsphere.shared.security.CurrentUser;
import com.skillsphere.skill.domain.Skill;
import com.skillsphere.skill.internal.SkillGraphService;
import com.skillsphere.skill.internal.SkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * The skill graph as a learner sees it.
 *
 * <p>Every endpoint here is scoped to the caller and reads the user id from the
 * security context rather than accepting it as a parameter. Taking it from the
 * request would mean any authenticated learner could ask for anyone else's
 * mastery simply by changing a number in the URL — the most common way this kind
 * of endpoint leaks data.
 */
@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
@Tag(name = "Skills", description = "The skill graph from the current learner's perspective")
public class SkillController {

    private final SkillService skillService;
    private final SkillGraphService graphService;

    /**
     * The mastery level at which a skill counts as learned.
     *
     * <p>Configurable rather than hard-coded because it is a pedagogical
     * judgement, not a technical constant: 0.80 means "we are 80% confident this
     * person has actually learned it", and the right figure is something an
     * institution should be able to argue about and change.
     */
    @Value("${skillsphere.adaptive.mastery-threshold:0.80}")
    private BigDecimal masteryThreshold;

    @GetMapping
    @Operation(summary = "The catalogue with this learner's mastery and availability",
            description = "Skills that are locked include the specific prerequisites still "
                    + "outstanding, so the client can explain the lock rather than just report it.")
    public List<SkillDtos.LearnerSkillResponse> list() {
        return skillService.listForLearner(CurrentUser.requireId(), masteryThreshold);
    }

    @GetMapping("/ready")
    @Operation(summary = "What this learner can start right now",
            description = "Not stored anywhere — derived from the graph and the learner's mastery. "
                    + "Two learners with different histories get different answers from identical data.")
    public List<SkillDtos.SkillRef> readyToLearn() {
        return graphService.findReadyToLearn(CurrentUser.requireId(), masteryThreshold).stream()
                .map(skill -> new SkillDtos.SkillRef(skill.getId(), skill.getName()))
                .toList();
    }

    @GetMapping("/roots")
    @Operation(summary = "Entry points into the graph",
            description = "Skills with no prerequisites — where someone with no history begins.")
    public List<SkillDtos.SkillRef> roots() {
        return graphService.findRootSkills().stream()
                .map(skill -> new SkillDtos.SkillRef(skill.getId(), skill.getName()))
                .toList();
    }

    @GetMapping("/{id}/prerequisites")
    @Operation(summary = "Everything this skill transitively depends on")
    public List<SkillDtos.SkillRef> prerequisites(
            @org.springframework.web.bind.annotation.PathVariable Long id) {
        return graphService.findAllPrerequisites(id).stream()
                .map(this::toRef)
                .toList();
    }

    @GetMapping("/{id}/unlocks")
    @Operation(summary = "Everything mastering this skill opens up",
            description = "Drives the skill-tree view: completing a node lights up the edges "
                    + "to everything it makes reachable.")
    public List<SkillDtos.SkillRef> unlocks(
            @org.springframework.web.bind.annotation.PathVariable Long id) {
        return graphService.findUnlockedBy(id).stream()
                .map(this::toRef)
                .toList();
    }

    private SkillDtos.SkillRef toRef(Skill skill) {
        return new SkillDtos.SkillRef(skill.getId(), skill.getName());
    }
}
