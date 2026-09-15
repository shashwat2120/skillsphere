package com.skillsphere.shared.assessment;

import com.skillsphere.assessment.ArenaItemSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Stands in for {@code assessment.internal.ArenaItemSourceService}, the same
 * way {@code SkillLookupShim} stands in for skill's own lookup — read-only
 * queries against the still-shared {@code items}/{@code item_options}
 * tables rather than a real call to assessment-service (bundled with skill
 * now, not this process). realtime's own code needed no changes: {@code
 * ArenaItemSource} never imported anything from {@code assessment.domain}
 * or {@code assessment.internal}, so the interface stayed exactly where
 * {@code ArenaService} already expected to find it.
 */
@Component
public class ArenaItemSourceShim implements ArenaItemSource {

    private final JdbcTemplate jdbc;

    public ArenaItemSourceShim(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArenaItem> selectItemsForSkill(Long skillId, int count) {
        List<Long> itemIds = jdbc.queryForList(
                "select id from items where skill_id = ? and status = 'ACTIVE' order by random() limit ?",
                Long.class, skillId, count);
        return itemIds.stream().map(this::toArenaItem).flatMap(Optional::stream).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ArenaItem> findById(Long itemId) {
        return toArenaItem(itemId);
    }

    @Override
    @Transactional(readOnly = true)
    public ScoredAnswer score(Long itemId, Long selectedOptionId) {
        Long correctOptionId = jdbc.query(
                        "select id from item_options where item_id = ? and is_correct = true limit 1",
                        (ResultSet rs, int n) -> rs.getLong("id"), itemId)
                .stream().findFirst().orElse(null);

        boolean correct = selectedOptionId != null && correctOptionId != null
                && selectedOptionId.equals(correctOptionId);

        return new ScoredAnswer(correct, correctOptionId);
    }

    private Optional<ArenaItem> toArenaItem(Long itemId) {
        List<String> stems = jdbc.query(
                "select stem from items where id = ?",
                (ResultSet rs, int n) -> rs.getString("stem"), itemId);
        if (stems.isEmpty()) {
            return Optional.empty();
        }

        List<ArenaOption> options = jdbc.query(
                "select id, text, position from item_options where item_id = ? order by position",
                (ResultSet rs, int n) -> new PositionedOption(rs.getLong("id"), rs.getString("text"), rs.getInt("position")),
                itemId).stream()
                .sorted(Comparator.comparing(PositionedOption::position))
                .map(o -> new ArenaOption(o.id(), o.text()))
                .toList();

        return Optional.of(new ArenaItem(itemId, stems.get(0), options));
    }

    private record PositionedOption(Long id, String text, int position) {
    }
}
