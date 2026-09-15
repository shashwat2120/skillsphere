package com.skillsphere.verification.internal;

import com.skillsphere.skill.SkillLookup;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stands in for {@code skill.internal.SkillLookupService}, reading this
 * service's own local {@code skills_mirror} (see V2__skills_mirror.sql)
 * instead of the shared {@code skills} table this shim used to query
 * cross-database. This module only ever calls {@link #namesOf} (see
 * VerificationController) — {@link #masteryOf} and
 * {@link #hardPrerequisitesOf} need data ({@code learner_skill_state},
 * {@code skill_prerequisites}) this service does not mirror, since nothing
 * here reads it; they throw rather than silently returning an empty
 * answer that could be mistaken for "no mastery yet" or "no
 * prerequisites".
 */
@Component
public class SkillLookupShim implements SkillLookup {

    private final JdbcTemplate jdbc;

    public SkillLookupShim(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SkillInfo> findById(Long skillId) {
        List<SkillInfo> rows = jdbc.query(
                "select id, slug, name, level_band, active from skills_mirror where id = ?",
                this::toSkillInfo, skillId);
        return rows.stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(Long skillId) {
        Integer count = jdbc.queryForObject("select count(*) from skills_mirror where id = ?", Integer.class, skillId);
        return count != null && count > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> namesOf(Collection<Long> skillIds) {
        if (skillIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new LinkedHashMap<>();
        List<Object[]> rows = jdbc.query(
                "select id, name from skills_mirror where id in (" + placeholders(skillIds.size()) + ")",
                (rs, rowNum) -> new Object[] {rs.getLong("id"), rs.getString("name")},
                skillIds.toArray());
        for (Object[] row : rows) {
            names.putIfAbsent((Long) row[0], (String) row[1]);
        }
        return names;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SkillInfo> findAllActive() {
        return jdbc.query(
                "select id, slug, name, level_band, active from skills_mirror where active = true order by name asc",
                this::toSkillInfo);
    }

    @Override
    public Map<Long, MasteryInfo> masteryOf(Long userId, Collection<Long> skillIds) {
        throw new UnsupportedOperationException(
                "verification-service does not mirror learner_skill_state — nothing here calls masteryOf");
    }

    @Override
    public List<Long> hardPrerequisitesOf(Long skillId) {
        throw new UnsupportedOperationException(
                "verification-service does not mirror skill_prerequisites — nothing here calls hardPrerequisitesOf");
    }

    private SkillInfo toSkillInfo(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new SkillInfo(rs.getLong("id"), rs.getString("slug"), rs.getString("name"),
                rs.getString("level_band"), rs.getBoolean("active"));
    }

    private String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }
}
