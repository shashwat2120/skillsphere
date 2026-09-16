package com.skillsphere.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
// Boot 4 relocated the MockMvc auto-configuration from
// org.springframework.test.web.servlet.autoconfigure into the boot webmvc-test
// module. Boot 3 test code will not compile against it.
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.redpanda.RedpandaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for tests that exercise the real stack.
 *
 * <p><b>Real PostgreSQL, not H2.</b> This is not thoroughness for its own sake.
 * Our schema depends on recursive CTEs for the skill graph, JSONB for the
 * explainability trace, partial indexes and CHECK constraints — none of which
 * H2 reproduces faithfully. A suite that passes against H2 would be testing a
 * database we do not ship, and the failures it hides are exactly the ones that
 * reach production.
 *
 * <p><b>Real Redis too</b>, because the denylist is a security control. Stubbing
 * it would mean the revocation tests verified a mock's behaviour rather than the
 * system's.
 *
 * <p><b>Real Redpanda too, and a private one.</b> Without this, every test run
 * published real events onto skill-catalog-events/skill-prerequisite-events —
 * the same shared broker docker-compose.yml's redpanda service backs for every
 * developer's own running services — permanently polluting every downstream
 * service's Kafka-synced read-model mirror with test-generated rows, including
 * some whose IDs happened to collide with real production skill IDs. That
 * actually happened during this project's own Sprint 6 work and needed a
 * by-hand cleanup. A throwaway container per test run closes that off entirely.
 *
 * <p>Containers are static so one Postgres, one Redis and one Redpanda are
 * shared across every test class in the run. Testcontainers reuses them via
 * the Ryuk lifecycle rather than paying container startup per class, which is
 * the difference between a suite people run and one they skip.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("skillsphere_test")
                    .withUsername("test")
                    .withPassword("test");

    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    // Same image tag as docker-compose.yml's own redpanda service, so the
    // broker under test matches the one every service actually runs against.
    @ServiceConnection
    static final RedpandaContainer REDPANDA =
            new RedpandaContainer(DockerImageName.parse("docker.redpanda.com/redpandadata/redpanda:v24.2.7"));

    static {
        POSTGRES.start();
        REDIS.start();
        REDPANDA.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
