package com.skillsphere.career.internal;

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
 * Stands in for {@code skill.internal.SkillLookupService}, split across
 * two sources now that assessment-service owns skill data in its own
 * database:
 *
 * <ul>
 *   <li>{@code findById}, {@code exists}, {@code namesOf}, {@code
 *   findAllActive}, {@code hardPrerequisitesOf} read this service's own
 *   local mirrors ({@code skills_mirror}, {@code
 *   skill_prerequisites_mirror} — see V2/V3 migrations), kept current by
 *   Kafka. Catalog data changes on the order of once a semester, so a
 *   permanent runtime dependency on assessment-service for these would be
 *   a worse trade than a synced local copy.
 *   <li>{@code masteryOf} delegates to {@link MasteryClient}, a real
 *   synchronous call. What a specific learner currently knows about a
 *   skill changes on every response they submit — a lagging mirror would
 *   show a learner as not-ready right after they proved otherwise, which
 *   {@code GapAnalysisService}'s readiness score cannot afford.
 * </ul>
 */
@Component
public class SkillLookupShim implements SkillLookup {

    private final JdbcTemplate jdbc;
    private final MasteryClient masteryClient;

    public SkillLookupShim(JdbcTemplate jdbc, MasteryClient masteryClient) {
        this.jdbc = jdbc;
        this.masteryClient = masteryClient;
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
        return masteryClient.masteryOf(userId, skillIds);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> hardPrerequisitesOf(Long skillId) {
        return jdbc.queryForList(
                "select prerequisite_skill_id from skill_prerequisites_mirror where skill_id = ? and strength >= 0.99",
                Long.class, skillId);
    }

    private SkillInfo toSkillInfo(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new SkillInfo(rs.getLong("id"), rs.getString("slug"), rs.getString("name"),
                rs.getString("level_band"), rs.getBoolean("active"));
    }

    private String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }
}
