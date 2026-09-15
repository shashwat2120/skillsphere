package com.skillsphere;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Career, extracted — Sprint 6's sixth service split. Role catalogue, gap
 * analysis, learning path generation, the skill passport.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CareerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CareerServiceApplication.class, args);
    }
}
