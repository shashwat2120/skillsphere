package com.skillsphere.career.internal;

import com.skillsphere.verification.EvidenceLookup;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Replaces {@code EvidenceLookupShim}'s cross-database JdbcTemplate query
 * — verification-service owns {@code evidence} (and the viva_sessions ->
 * submissions -> projects walk that resolves a source's display title) in
 * its own database now. Calls verification-service's
 * {@code /internal/evidence} directly, load-balanced against Eureka via
 * {@link LoadBalancedClientConfig}, rather than mirroring evidence
 * locally: this is per-learner, request-shaped data a passport view needs
 * complete and current, not a small catalog every service could
 * reasonably cache — same reasoning as {@link MasteryClient}, at a lower
 * frequency (evidence changes once per verification, not once per
 * response) but the same shape of read.
 */
@Component
public class EvidenceLookupHttpClient implements EvidenceLookup {

    private final RestClient restClient;

    public EvidenceLookupHttpClient(@LoadBalanced RestClient.Builder loadBalancedRestClientBuilder) {
        this.restClient = loadBalancedRestClientBuilder.baseUrl("http://verification-service").build();
    }

    @Override
    public List<EvidenceItem> findByUserId(Long userId) {
        List<EvidenceItem> response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/internal/evidence")
                        .queryParam("userId", userId)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<EvidenceItem>>() {
                });

        return response == null ? List.of() : response;
    }
}
