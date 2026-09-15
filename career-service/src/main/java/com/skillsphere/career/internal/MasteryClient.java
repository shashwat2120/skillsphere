package com.skillsphere.career.internal;

import com.skillsphere.skill.SkillLookup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The live half of {@link SkillLookupShim} — see that class's own comment
 * for why {@code masteryOf} cannot be served from a mirror the way
 * {@code namesOf}/{@code findAllActive}/{@code hardPrerequisitesOf} are.
 * Calls assessment-service's {@code /internal/skills/mastery} directly,
 * load-balanced against Eureka via {@link LoadBalancedClientConfig}.
 */
@Slf4j
@Component
public class MasteryClient {

    /** JSON object keys are always strings; assessment-service's Map<Long, MasteryInfo> arrives keyed by string. */
    private record MasteryResponse(double masteryProbability, double abilityTheta, int responseCount) {
    }

    private final RestClient restClient;

    public MasteryClient(@LoadBalanced RestClient.Builder loadBalancedRestClientBuilder) {
        this.restClient = loadBalancedRestClientBuilder.baseUrl("http://assessment-service").build();
    }

    public Map<Long, SkillLookup.MasteryInfo> masteryOf(Long userId, Collection<Long> skillIds) {
        if (skillIds.isEmpty()) {
            return Map.of();
        }

        String idsParam = skillIds.stream().map(String::valueOf).collect(Collectors.joining(","));

        Map<String, MasteryResponse> response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/internal/skills/mastery")
                        .queryParam("userId", userId)
                        .queryParam("skillIds", idsParam)
                        .build())
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<Map<String, MasteryResponse>>() {
                });

        if (response == null) {
            return Map.of();
        }

        Map<Long, SkillLookup.MasteryInfo> result = new LinkedHashMap<>();
        response.forEach((skillId, m) -> result.put(Long.valueOf(skillId),
                new SkillLookup.MasteryInfo(m.masteryProbability(), m.abilityTheta(), m.responseCount())));
        return result;
    }
}
