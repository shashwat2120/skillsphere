package com.skillsphere.realtime.internal;

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
 * Stands in for {@code skill.internal.SkillLookupService} now that skill's
 * real code lives in assessment-service, not this process — an identical
 * copy of the same shim content-service, analytics-service, career-
 * service and verification-service each carry. This module only ever
 * calls {@code findById}, for a skill's name in a confusion alert or
 * arena summary. Read-only queries against the
 * same shared tables, the same interim-shared-database arrangement every
 * other extracted service's own shim uses. The real HTTP client to
 * assessment-service (skill bundled in) is the work this still defers.
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
                "select id, slug, name, level_band, is_active from skills where id = ?",
                this::toSkillInfo, skillId);
        return rows.stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(Long skillId) {
        Integer count = jdbc.queryForObject("select count(*) from skills where id = ?", Integer.class, skillId);
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
                "select id, name from skills where id in (" + placeholders(skillIds.size()) + ")",
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
                "select id, slug, name, level_band, is_active from skills where is_active = true order by name asc",
                this::toSkillInfo);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, MasteryInfo> masteryOf(Long userId, Collection<Long> skillIds) {
        if (skillIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, MasteryInfo> result = new LinkedHashMap<>();
        Object[] args = concat(userId, skillIds);
        List<Map.Entry<Long, MasteryInfo>> rows = jdbc.query(
                "select skill_id, mastery_probability, ability_theta, attempts_count "
                        + "from learner_skill_state where user_id = ? and skill_id in (" + placeholders(skillIds.size()) + ")",
                (rs, rowNum) -> Map.entry(rs.getLong("skill_id"), new MasteryInfo(
                        rs.getDouble("mastery_probability"),
                        rs.getDouble("ability_theta"),
                        rs.getInt("attempts_count"))),
                args);
        for (Map.Entry<Long, MasteryInfo> row : rows) {
            result.put(row.getKey(), row.getValue());
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> hardPrerequisitesOf(Long skillId) {
        return jdbc.queryForList(
                "select prerequisite_skill_id from skill_prerequisites where skill_id = ? and strength >= 0.99",
                Long.class, skillId);
    }

    private SkillInfo toSkillInfo(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new SkillInfo(rs.getLong("id"), rs.getString("slug"), rs.getString("name"),
                rs.getString("level_band"), rs.getBoolean("is_active"));
    }

    private String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private Object[] concat(Long first, Collection<Long> rest) {
        Object[] args = new Object[rest.size() + 1];
        args[0] = first;
        int i = 1;
        for (Long id : rest) {
            args[i++] = id;
        }
        return args;
    }
}
