package com.skillsphere.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Instant;
import java.util.Optional;

/**
 * Enables JPA auditing so {@code @CreatedDate} and {@code @LastModifiedDate} on
 * {@link com.skillsphere.shared.domain.AuditableEntity} are populated.
 *
 * <p>The explicit {@link DateTimeProvider} pins auditing to UTC instants rather
 * than the JVM default clock. Left to the default, timestamps would follow
 * whatever zone the host happens to run in, which silently differs between a
 * developer laptop, CI and a server — and our streaks, evidence verification
 * times and decay calculations all depend on those timestamps being comparable
 * across machines.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "utcDateTimeProvider")
public class JpaConfig {

    @Bean
    public DateTimeProvider utcDateTimeProvider() {
        return () -> Optional.of(Instant.now());
    }
}
