package com.skillsphere.assessment;

import java.util.List;
import java.util.Optional;

/**
 * Assessment's public API for the live arena — questions from the same item
 * bank the adaptive diagnostic uses, scored the same server-side-only way.
 *
 * <p>{@link ArenaItem} carries no correct-option flag, for the same reason
 * {@code DiagnosticDtos.ItemView} doesn't: an arena payload broadcast to
 * every participant's browser is trivially inspectable, and shipping the
 * answer key in it is the most common way a quiz API gets defeated.
 * {@link #score} is the only place the correct answer is ever revealed, and
 * only as a boolean plus which option it was — never handed back as data a
 * client could cache and reuse next round.
 */
public interface ArenaItemSource {

    record ArenaOption(Long id, String text) {
    }

    record ArenaItem(Long id, String stem, List<ArenaOption> options) {
    }

    record ScoredAnswer(boolean correct, Long correctOptionId) {
    }

    /**
     * A fixed set of items for one arena, chosen once at creation time so the
     * whole room answers the same questions in the same order — unlike the
     * adaptive diagnostic, an arena is a shared, simultaneous event, and
     * personalising the questions would break that.
     */
    List<ArenaItem> selectItemsForSkill(Long skillId, int count);

    Optional<ArenaItem> findById(Long itemId);

    ScoredAnswer score(Long itemId, Long selectedOptionId);
}
