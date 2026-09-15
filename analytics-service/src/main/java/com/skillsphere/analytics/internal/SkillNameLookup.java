package com.skillsphere.analytics.internal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The one piece of skill data this service still needs: a name to put next
 * to a failure-cluster skill id in a risk score's factors.
 *
 * <p>Reads this service's own local {@code skills_mirror} (see
 * V2__skills_mirror.sql) instead of assessment-service's {@code skills}
 * table directly — that table lives in a different database now.
 * SkillCatalogEventListener keeps the mirror current; nothing here writes
 * it.
 */
@Component
public class SkillNameLookup {

    private final JdbcTemplate jdbc;

    public SkillNameLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> nameOf(Long skillId) {
        return jdbc.query(
                "select name from skills_mirror where id = ?",
                rs -> rs.next() ? Optional.of(rs.getString("name")) : Optional.empty(),
                skillId);
    }
}
