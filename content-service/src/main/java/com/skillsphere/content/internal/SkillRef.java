package com.skillsphere.content.internal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * This service's own stand-in for {@code skill.SkillLookup}, the same interim
 * arrangement analytics-service uses for the identical dependency (see that
 * project's {@code SkillNameLookup}): skill is still part of the monolith,
 * so a real HTTP client would call a service that does not exist yet.
 * Read-only queries against the still-shared {@code skills} table instead,
 * matching {@code SkillLookupService}'s own semantics exactly —
 * {@code findById} is unfiltered (any id, active or not, matching the
 * original's "validate this id exists" use in tagging), {@code findAllActive}
 * filters {@code is_active} and orders by name (matching the original's use
 * in building a display map).
 */
@Component
public class SkillRef {

    public record SkillSummary(Long id, String name) {
    }

    private final JdbcTemplate jdbc;

    public SkillRef(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<SkillSummary> findById(Long skillId) {
        return jdbc.query(
                "select id, name from skills where id = ?",
                rs -> rs.next()
                        ? Optional.of(new SkillSummary(rs.getLong("id"), rs.getString("name")))
                        : Optional.empty(),
                skillId);
    }

    public List<SkillSummary> findAllActive() {
        return jdbc.query(
                "select id, name from skills where is_active = true order by name asc",
                (rs, rowNum) -> new SkillSummary(rs.getLong("id"), rs.getString("name")));
    }
}
