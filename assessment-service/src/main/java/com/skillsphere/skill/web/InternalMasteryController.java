package com.skillsphere.skill.web;

import com.skillsphere.skill.SkillLookup;
import com.skillsphere.skill.internal.SkillLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;


/**
 * Service-to-service only — never routed through the gateway (see
 * GatewayRoutes; it only ever maps {@code /api/**}, and this lives under
 * {@code /internal}), and reachable only within the Docker network / local
 * dev host, never from a browser.
 *
 * <p>The one genuinely live cross-service read this split has: {@link
 * SkillLookup#masteryOf} answers "what does this learner currently know
 * about these skills", which changes on every response they submit.
 * career-service's readiness score would be visibly wrong — showing a
 * learner as not-ready moments after they proved otherwise — if this came
 * from a mirror lagging behind by even one Kafka round trip, unlike the
 * catalog data (skill names, prerequisite edges) that mirrors happily
 * because it barely ever changes. See career-service's MasteryClient for
 * the caller side of this contract.
 */
@RestController
@RequiredArgsConstructor
public class InternalMasteryController {

    private final SkillLookupService skillLookup;

    @GetMapping("/internal/skills/mastery")
    public Map<Long, SkillLookup.MasteryInfo> mastery(
            @RequestParam Long userId, @RequestParam List<Long> skillIds) {
        return skillLookup.masteryOf(userId, skillIds);
    }
}
