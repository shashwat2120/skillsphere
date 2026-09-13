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
 * <p>Containers are static so one Postgres and one Redis are shared across every
 * test class in the run. Testcontainers reuses them via the Ryuk lifecycle
 * rather than paying container startup per class, which is the difference
 * between a suite people run and one they skip.
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

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
