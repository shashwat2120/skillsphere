package com.skillsphere;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Identity, extracted — Sprint 6's first real service split.
 *
 * <p>The package layout is deliberately unchanged from the monolith:
 * {@code com.skillsphere.identity} and {@code com.skillsphere.shared} sit
 * exactly where they always did, so this is genuinely the same module
 * moved into its own process, not a rewrite. {@code shared} carries only
 * what identity itself needs — error handling, the caller-identity helper,
 * rate limiting, and the admin audit trail, which this service is now the
 * sole owner of.
 *
 * <p>{@code @EnableAsync} matters for the same reason it did in the
 * monolith: {@code @ApplicationModuleListener} (used by
 * {@link com.skillsphere.identity.internal.KafkaEventBridge}) is built on
 * {@code @Async}, and the event bridge must never sit on the request path
 * that registration or login runs on.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
public class IdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
