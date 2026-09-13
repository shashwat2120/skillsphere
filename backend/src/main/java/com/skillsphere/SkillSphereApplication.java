package com.skillsphere;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SkillSphere — a learning platform where every claim about what a person
 * knows comes with evidence that can be checked.
 *
 * <p>Sprint 5 ships this as a modular monolith. Each top-level package under
 * {@code com.skillsphere} is a module with its own API, domain and internals,
 * and modules talk to each other through application events rather than
 * reaching into one another's classes. That discipline is what makes the
 * Sprint 6 microservices split an extraction rather than a rewrite.
 *
 * <p>{@code @EnableAsync} matters for more than tidiness: mail, analytics
 * event writes and inference calls must never sit on the request path.
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class SkillSphereApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkillSphereApplication.class, args);
    }
}
