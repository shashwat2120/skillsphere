package com.skillsphere;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Verification, extracted — Sprint 6's seventh service split. Projects, the
 * process ledger, the AI viva, the evidence store — isolated by design
 * since Sprint 5, for the CPU-heavy local LLM inference its own workload
 * needs (see this project's own pom.xml).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class VerificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(VerificationServiceApplication.class, args);
    }
}
