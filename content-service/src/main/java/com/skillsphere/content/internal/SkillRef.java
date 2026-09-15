package com.skillsphere.content.internal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * This service's own stand-in for {@code skill.SkillLookup}, reading the
 * local {@code skills_mirror} table (see V2__skills_mirror.sql) instead of
 * assessment-service's {@code skills} directly — that table lives in a
 * different database now. {@code findById} is unfiltered (any id, active
 * or not, matching the original's "validate this id exists" use in
 * tagging), {@code findAllActive} filters {@code active} and orders by
 * name (matching the original's use in building a display map).
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
                "select id, name from skills_mirror where id = ?",
                rs -> rs.next()
                        ? Optional.of(new SkillSummary(rs.getLong("id"), rs.getString("name")))
                        : Optional.empty(),
                skillId);
    }

    public List<SkillSummary> findAllActive() {
        return jdbc.query(
                "select id, name from skills_mirror where active = true order by name asc",
                (rs, rowNum) -> new SkillSummary(rs.getLong("id"), rs.getString("name")));
    }
}
