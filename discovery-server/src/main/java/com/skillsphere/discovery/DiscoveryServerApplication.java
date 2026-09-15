package com.skillsphere.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * The service registry. Every Sprint 6 service — the gateway included —
 * registers itself here on startup and asks here for everyone else, rather
 * than any service holding another's host and port directly.
 *
 * <p>Runs as its own instance, deliberately not registered with itself
 * ({@code register-with-eureka: false}, {@code fetch-registry: false} in
 * application.yml) — the registry does not need to discover other services
 * to do its one job, and a self-registering registry is a needless edge
 * case (what does it mean for the registry to look itself up?) for no
 * benefit at this scale.
 */
@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServerApplication.class, args);
    }
}
