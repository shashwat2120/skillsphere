package com.skillsphere;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Content, extracted — Sprint 6's fourth service split. Courses, modules,
 * lessons — no events, no other module's Java dependency on this one.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ContentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContentServiceApplication.class, args);
    }
}
