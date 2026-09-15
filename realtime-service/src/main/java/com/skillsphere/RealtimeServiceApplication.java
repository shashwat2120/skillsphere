package com.skillsphere;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Realtime, extracted — Sprint 6's eighth and final planned service split.
 * WebSocket/STOMP infrastructure, the live arena, instructor confusion
 * alerts — separated since Sprint 5 because it scales on connections, not
 * request rate.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class RealtimeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RealtimeServiceApplication.class, args);
    }
}
