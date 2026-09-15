package com.skillsphere.career.internal;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * The one {@code @LoadBalanced} builder every genuinely-live cross-service
 * call in this service shares — {@link MasteryClient} (assessment-service)
 * and {@link EvidenceLookupHttpClient} (verification-service). Spring
 * Cloud LoadBalancer resolves {@code http://<service-name>/...} against
 * Eureka the same way the gateway's own {@code lb()} filter does, so
 * callers write the service's registered name, not a host:port.
 */
@Configuration
public class LoadBalancedClientConfig {

    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}
