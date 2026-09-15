package com.skillsphere.assessment;

import java.util.List;

/**
 * Published whenever an item's arena-relevant state changes — activated or
 * retired. Draft items are excluded on purpose: {@link ArenaItemSource
 * #selectItemsForSkill} only ever draws from {@code status = 'ACTIVE'}
 * items, so realtime-service's local mirror (the only consumer of this
 * event) has no use for one that has never gone live.
 *
 * <p>Carries the item's full arena-facing shape — stem and every option,
 * including {@code correct} — because {@link ArenaItemSource#score} needs
 * to grade an answer without a network call back to this service on every
 * live-arena question. That answer key never leaves realtime-service's own
 * backend; see {@link ArenaItemSource}'s own class comment for why it is
 * never shipped to a browser.
 */
public record ItemCatalogChanged(
        Long itemId,
        Long skillId,
        String stem,
        String status,
        List<OptionInfo> options) {

    public record OptionInfo(Long id, String text, boolean correct, int position) {
    }
}
