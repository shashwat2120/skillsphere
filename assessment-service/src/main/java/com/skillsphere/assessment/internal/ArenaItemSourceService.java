package com.skillsphere.assessment.internal;

import com.skillsphere.assessment.ArenaItemSource;
import com.skillsphere.assessment.domain.Item;
import com.skillsphere.assessment.domain.ItemOption;
import com.skillsphere.assessment.domain.ItemOptionRepository;
import com.skillsphere.assessment.domain.ItemRepository;
import com.skillsphere.assessment.domain.ItemStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/** Implements assessment's public API for the live arena. */
@Service
@RequiredArgsConstructor
public class ArenaItemSourceService implements ArenaItemSource {

    private final ItemRepository items;
    private final ItemOptionRepository options;
    private final Random random = new Random();

    @Override
    @Transactional(readOnly = true)
    public List<ArenaItem> selectItemsForSkill(Long skillId, int count) {
        List<Item> candidates = new java.util.ArrayList<>(
                items.findBySkillIdAndStatus(skillId, ItemStatus.ACTIVE));
        Collections.shuffle(candidates, random);
        return candidates.stream().limit(count).map(this::toArenaItem).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ArenaItem> findById(Long itemId) {
        return items.findById(itemId).map(this::toArenaItem);
    }

    @Override
    @Transactional(readOnly = true)
    public ScoredAnswer score(Long itemId, Long selectedOptionId) {
        List<ItemOption> itemOptions = options.findByItemIdOrderByPosition(itemId);
        ItemOption correctOption = itemOptions.stream()
                .filter(ItemOption::isCorrect)
                .findFirst()
                .orElse(null);
        boolean correct = selectedOptionId != null
                && itemOptions.stream()
                    .filter(o -> o.getId().equals(selectedOptionId))
                    .findFirst()
                    .map(ItemOption::isCorrect)
                    .orElse(false);
        return new ScoredAnswer(correct, correctOption == null ? null : correctOption.getId());
    }

    private ArenaItem toArenaItem(Item item) {
        List<ArenaOption> arenaOptions = options.findByItemIdOrderByPosition(item.getId()).stream()
                .sorted(Comparator.comparing(ItemOption::getPosition))
                .map(o -> new ArenaOption(o.getId(), o.getText()))
                .toList();
        return new ArenaItem(item.getId(), item.getStem(), arenaOptions);
    }
}
