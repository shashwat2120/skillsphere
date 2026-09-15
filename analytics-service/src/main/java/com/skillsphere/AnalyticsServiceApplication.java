package com.skillsphere;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Analytics, extracted — Sprint 6's third service split.
 *
 * <p>{@code @EnableScheduling} is new relative to identity-service and
 * notification-service: {@link com.skillsphere.analytics.internal.RiskScoringService}
 * runs its recompute pass every five minutes here now, not in the monolith.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
@EnableScheduling
public class AnalyticsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyticsServiceApplication.class, args);
    }
}
