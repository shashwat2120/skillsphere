package com.skillsphere.career.internal;

import com.skillsphere.shared.error.ServiceUnavailableException;
import com.skillsphere.verification.EvidenceLookup;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
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
 *
 * <p>{@code @CircuitBreaker}/{@code @Retry} and the fail-loud fallback
 * follow {@link MasteryClient}'s exact reasoning: an empty evidence list
 * on failure would read as "this learner has no verified skills", which
 * is as false and as damaging a claim as a fabricated zero mastery score
 * — a skill passport with silently-missing evidence is worse than one
 * that visibly failed to load.
 */
@Slf4j
@Component
public class EvidenceLookupHttpClient implements EvidenceLookup {

    private static final String CB_NAME = "verification-service-evidence";

    private final RestClient restClient;

    public EvidenceLookupHttpClient(@LoadBalanced RestClient.Builder loadBalancedRestClientBuilder) {
        this.restClient = loadBalancedRestClientBuilder.baseUrl("http://verification-service").build();
    }

    @Override
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "evidenceUnavailable")
    @Retry(name = CB_NAME)
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

    @SuppressWarnings("unused")
    private List<EvidenceItem> evidenceUnavailable(Long userId, Throwable cause) {
        log.warn("verification-service unreachable for evidence lookup (user {}): {}", userId, cause.toString());
        throw new ServiceUnavailableException("VERIFICATION_SERVICE_UNAVAILABLE",
                "Verified skill evidence is temporarily unavailable — verification-service isn't "
                        + "responding. Try again shortly.", cause);
    }
}
