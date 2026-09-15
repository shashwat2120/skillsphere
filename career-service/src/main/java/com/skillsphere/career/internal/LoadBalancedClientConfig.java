package com.skillsphere.career.internal;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * The one {@code @LoadBalanced} builder every genuinely-live cross-service
 * call in this service shares — {@link MasteryClient} (assessment-service)
 * and {@link EvidenceLookupHttpClient} (verification-service). Spring
 * Cloud LoadBalancer resolves {@code http://<service-name>/...} against
 * Eureka the same way the gateway's own {@code lb()} filter does, so
 * callers write the service's registered name, not a host:port.
 *
 * <p>{@code defaultRestClientBuilder} exists to stop a genuine circular
 * bootstrap deadlock this service hit live: {@code @LoadBalanced} is a
 * marker <em>qualifier</em>, not a distinct bean type, so if this were the
 * only {@code RestClient.Builder} bean in the context, every unqualified
 * injection point — including Spring Cloud Netflix Eureka's own internal
 * HTTP transport, which uses a {@code RestClient} to talk to the Eureka
 * server itself — would receive the load-balanced one too. That transport
 * then needed the load balancer's own service-instance lookup to resolve
 * "localhost", which needed Eureka's registry, which needed that same
 * transport to fetch — an unresolvable {@code BeanCurrentlyInCreation}
 * cycle, confirmed from the actual stack trace this service logged
 * ({@code scopedTarget.eurekaClient} circularly depending on itself
 * through {@code DiscoveryClientServiceInstanceListSupplier}). Marking
 * this one {@code @Primary} gives every unqualified caller (Eureka
 * included) a plain, un-intercepted builder, while {@link MasteryClient}
 * and {@link EvidenceLookupHttpClient} still get the load-balanced one by
 * asking for it explicitly via the {@code @LoadBalanced} qualifier.
 */
@Configuration
public class LoadBalancedClientConfig {

    @Bean
    @Primary
    public RestClient.Builder defaultRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        // No timeout at all is the actual default here otherwise — a
        // hung assessment-service or verification-service would block a
        // request thread indefinitely rather than failing fast enough
        // for MasteryClient/EvidenceLookupHttpClient's own circuit
        // breaker and retry (see application.yml's resilience4j.*
        // config) to ever see a failure to react to.
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect()
                .build(HttpClientSettings.defaults()
                        .withConnectTimeout(Duration.ofSeconds(2))
                        .withReadTimeout(Duration.ofSeconds(3)));
        return RestClient.builder().requestFactory(requestFactory);
    }
}
