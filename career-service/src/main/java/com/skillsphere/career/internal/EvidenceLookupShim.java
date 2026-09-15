package com.skillsphere.career.internal;

import com.skillsphere.verification.EvidenceLookup;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Stands in for {@code verification.internal.EvidenceLookupService} — the
 * same interim-shared-database pattern as {@link SkillLookupShim}, this
 * time for the one thing career reads from verification (not yet
 * extracted, still in the monolith): the evidence list behind the skill
 * passport. {@code EvidenceLookup} was always self-contained, so nothing
 * about {@code PassportService}'s own code needed to change, only which
 * bean implements the interface it depends on.
 *
 * <p>Replicates {@code EvidenceLookupService}'s exact {@code sourceTitle}
 * resolution: everything except a VIVA_SESSION source reports its own
 * source-type name; a viva session's title is the project it defended,
 * found by walking {@code viva_sessions -> submissions -> projects} —
 * one row at a time rather than batched, the same deliberate
 * simplification the original made ("a single learner's evidence table
 * has, at most, a handful of rows for the lifetime of this project").
 */
@Component
public class EvidenceLookupShim implements EvidenceLookup {

    private final JdbcTemplate jdbc;

    public EvidenceLookupShim(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvidenceItem> findByUserId(Long userId) {
        return jdbc.query(
                "select id, skill_id, evidence_type, source_type, source_id, weight, score, verified_at "
                        + "from evidence where user_id = ? order by verified_at desc",
                (rs, rowNum) -> toItem(rs),
                userId);
    }

    private EvidenceItem toItem(ResultSet rs) throws SQLException {
        String sourceType = rs.getString("source_type");
        Long sourceId = rs.getLong("source_id");
        double weight = rs.getDouble("weight");
        double score = rs.getObject("score") == null ? 0.0 : rs.getDouble("score");
        Instant verifiedAt = rs.getTimestamp("verified_at") == null
                ? null : rs.getTimestamp("verified_at").toInstant();

        return new EvidenceItem(rs.getLong("id"), rs.getLong("skill_id"), rs.getString("evidence_type"),
                sourceType, sourceId, resolveSourceTitle(sourceType, sourceId), weight, score, verifiedAt);
    }

    private String resolveSourceTitle(String sourceType, Long sourceId) {
        if (!"VIVA_SESSION".equals(sourceType)) {
            return sourceType;
        }
        return findSubmissionId(sourceId)
                .flatMap(this::findProjectId)
                .flatMap(this::findProjectTitle)
                .orElse("Project");
    }

    private Optional<Long> findSubmissionId(Long vivaSessionId) {
        return jdbc.query("select submission_id from viva_sessions where id = ?",
                (rs, n) -> rs.getLong("submission_id"), vivaSessionId).stream().findFirst();
    }

    private Optional<Long> findProjectId(Long submissionId) {
        return jdbc.query("select project_id from submissions where id = ?",
                (rs, n) -> rs.getLong("project_id"), submissionId).stream().findFirst();
    }

    private Optional<String> findProjectTitle(Long projectId) {
        return jdbc.query("select title from projects where id = ?",
                (rs, n) -> rs.getString("title"), projectId).stream().findFirst();
    }
}
