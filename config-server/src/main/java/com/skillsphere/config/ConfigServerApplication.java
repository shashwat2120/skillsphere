package com.skillsphere.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Centralised configuration for every Sprint 6 service. Each service asks
 * this one place for its config at startup ({@code spring.config.import:
 * optional:configserver:...}) instead of carrying its own copy — the
 * "optional:" prefix matters: if this server is ever down, a dependent
 * service still starts with whatever it has locally rather than refusing
 * to boot over a missing convenience.
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
