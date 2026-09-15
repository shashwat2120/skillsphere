package com.skillsphere;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Assessment and skill, extracted together — Sprint 6's fifth service
 * split. See this project's pom.xml for why these two Sprint 5 modules
 * ship as one process rather than two.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
public class AssessmentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AssessmentServiceApplication.class, args);
    }
}
