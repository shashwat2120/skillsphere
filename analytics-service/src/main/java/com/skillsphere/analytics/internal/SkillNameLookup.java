package com.skillsphere.analytics.internal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The one piece of skill data this service still needs: a name to put next
 * to a failure-cluster skill id in a risk score's factors.
 *
 * <p>Not a Kafka event (nothing about a skill's name changing is a fact this
 * service needs to react to) and not an HTTP call to a skill-service that
 * does not exist yet — skill is still part of the monolith. Same interim
 * arrangement identity-service uses for the {@code users} table: this
 * service and the monolith still share one physical Postgres for now (see
 * analytics-service's own pom.xml), so a plain read-only query against a
 * table this service does not own is a smaller, more honest compromise than
 * either building a premature HTTP client or duplicating skill's data here.
 * The real fix — an HTTP call to skill-service — is exactly the work
 * {@code skill.SkillLookup}'s own doc comment already describes and defers
 * to the day skill itself is extracted.
 */
@Component
public class SkillNameLookup {

    private final JdbcTemplate jdbc;

    public SkillNameLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> nameOf(Long skillId) {
        return jdbc.query(
                "select name from skills where id = ?",
                rs -> rs.next() ? Optional.of(rs.getString("name")) : Optional.empty(),
                skillId);
    }
}
