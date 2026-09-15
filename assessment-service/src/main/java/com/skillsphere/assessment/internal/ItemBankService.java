package com.skillsphere.assessment.internal;

import com.skillsphere.assessment.domain.*;
import com.skillsphere.assessment.web.ItemDtos;
import com.skillsphere.shared.error.ForbiddenException;
import com.skillsphere.shared.error.NotFoundException;
import com.skillsphere.shared.error.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Authoring and validation of assessment items.
 *
 * <p>Most of this class is validation, and that is the point. An item is a
 * measuring instrument: a broken one does not fail loudly, it quietly produces
 * wrong measurements that the engine then treats as fact and writes into someone's
 * skill passport. Catching a bad item at authoring time is far cheaper than
 * discovering months later that a cohort was certified on a question with two
 * correct answers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItemBankService {

    /** Below this, learned parameters are noise and the author's prior is used. */
    private static final int CALIBRATION_THRESHOLD = 30;

    private final ItemRepository items;
    private final ItemOptionRepository options;
    private final MisconceptionRepository misconceptions;

    @Transactional
    public ItemDtos.ItemResponse create(ItemDtos.CreateItemRequest request, Long authorId) {
        validateOptions(request.options());

        Item item = new Item(request.skillId(), request.stem(), authorId);
        item.setType(request.type() == null ? ItemType.MCQ : request.type());
        item.setExplanation(request.explanation());
        if (request.declaredDifficulty() != null) {
            item.setDeclaredDifficulty(request.declaredDifficulty());
        }
        items.save(item);

        int position = 0;
        for (ItemDtos.OptionInput input : request.options()) {
            ItemOption option = new ItemOption(item, input.text(), input.correct(), position++);
            if (!input.correct() && input.misconceptionId() != null) {
                // Verified rather than trusted: a dangling misconception id would
                // produce a diagnosis that silently resolves to nothing at the
                // moment a learner most needs an explanation.
                misconceptions.findById(input.misconceptionId())
                        .orElseThrow(() -> new NotFoundException("Misconception", input.misconceptionId()));
                option.setMisconceptionId(input.misconceptionId());
            }
            options.save(option);
        }

        log.info("Item {} authored for skill {} by user {}", item.getId(), request.skillId(), authorId);
        return toResponse(item);
    }

    /**
     * Checks the structural rules an item must satisfy to measure anything.
     *
     * <p>Each rule exists because violating it produces a specific wrong
     * measurement rather than an obvious error.
     */
    private void validateOptions(List<ItemDtos.OptionInput> inputs) {
        if (inputs == null || inputs.size() < 2) {
            throw new ValidationException("TOO_FEW_OPTIONS",
                    "An item needs at least two options.");
        }

        long correctCount = inputs.stream().filter(ItemDtos.OptionInput::correct).count();

        if (correctCount == 0) {
            // Unanswerable. Every learner is marked wrong, and the engine reads
            // that as an entire cohort lacking the skill.
            throw new ValidationException("NO_CORRECT_OPTION",
                    "Exactly one option must be marked correct.");
        }
        if (correctCount > 1) {
            // Worse than no correct answer, because it looks fine. Learners who
            // pick the other valid option are marked wrong and have their
            // mastery reduced for being right.
            throw new ValidationException("MULTIPLE_CORRECT_OPTIONS",
                    "Exactly one option must be marked correct.");
        }

        boolean correctCarriesMisconception = inputs.stream()
                .anyMatch(input -> input.correct() && input.misconceptionId() != null);
        if (correctCarriesMisconception) {
            throw new ValidationException("CORRECT_OPTION_HAS_MISCONCEPTION",
                    "A correct answer cannot carry a misconception.");
        }
    }

    @Transactional
    public void activate(Long itemId, Long actingUserId, boolean isAdmin) {
        Item item = requireOwned(itemId, actingUserId, isAdmin);

        List<ItemOption> itemOptions = options.findByItemIdOrderByPosition(itemId);
        long untagged = itemOptions.stream()
                .filter(option -> !option.isCorrect() && option.getMisconceptionId() == null)
                .count();

        if (untagged > 0) {
            // A warning rather than a rejection. An untagged distractor still
            // functions as an assessment; it simply teaches the platform nothing
            // about why the learner was wrong, which is the capability that makes
            // this product different. Blocking activation outright would push
            // authors toward inventing misconceptions to satisfy a rule, and a
            // fabricated diagnosis is worse than none.
            log.warn("Item {} activated with {} untagged distractor(s) — diagnosis will be limited",
                    itemId, untagged);
        }

        item.setStatus(ItemStatus.ACTIVE);
    }

    @Transactional
    public void retire(Long itemId, Long actingUserId, boolean isAdmin) {
        Item item = requireOwned(itemId, actingUserId, isAdmin);
        // Retired, never deleted. Responses to this item are the evidence behind
        // skill claims already issued; removing it would erase the working behind
        // a learner's passport rather than merely stopping the question.
        item.setStatus(ItemStatus.RETIRED);
        log.info("Item {} retired by user {}", itemId, actingUserId);
    }

    /**
     * Marks an item calibrated once it has enough responses.
     *
     * <p>Until then {@link Item#effectiveDifficulty()} falls back to the author's
     * declared prior. Trusting a learned difficulty derived from three responses
     * would be worse than trusting the guess it replaced.
     */
    @Transactional
    public void recalculateCalibration(Long itemId) {
        Item item = items.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Item", itemId));
        if (!item.isCalibrated() && item.getTimesSeen() >= CALIBRATION_THRESHOLD) {
            item.setCalibrated(true);
            log.info("Item {} is now calibrated after {} responses", itemId, item.getTimesSeen());
        }
    }

    /**
     * Every item for a skill, any status, newest first.
     *
     * <p>Deliberately not filtered to {@code ACTIVE}. This is the authoring
     * view — the one place an instructor manages their own bank — and a
     * just-created {@code DRAFT} item that vanished from its own list until
     * activated would be a dead end with no way back to it. The learner-
     * facing selection path ({@link ItemRepository#findUnseenBySkill}) is a
     * separate query and is unaffected: it was always scoped to ACTIVE and
     * still is.
     */
    @Transactional(readOnly = true)
    public List<ItemDtos.ItemResponse> listBySkill(Long skillId) {
        return items.findBySkillIdOrderByIdDesc(skillId).stream()
                .map(this::toResponse).toList();
    }

    /**
     * Items whose measured behaviour suggests they are broken.
     *
     * <p>This is quality control the platform performs on itself. Without it, a
     * bad item keeps being asked indefinitely and keeps corrupting the estimates
     * that depend on it.
     */
    @Transactional(readOnly = true)
    public List<ItemDtos.ItemHealthResponse> findSuspectItems() {
        return items.findSuspectItems(CALIBRATION_THRESHOLD).stream()
                .map(item -> new ItemDtos.ItemHealthResponse(
                        item.getId(), item.getStem(), item.getSkillId(),
                        item.getTimesSeen(), item.pValue(), item.getDiscriminationA(),
                        diagnose(item)))
                .toList();
    }

    private String diagnose(Item item) {
        java.math.BigDecimal p = item.pValue();
        if (p == null) {
            return "Not enough responses to judge.";
        }
        if (p.doubleValue() > 0.95) {
            return "Almost everyone answers correctly — this item separates nobody.";
        }
        if (p.doubleValue() < 0.15) {
            return "Almost nobody answers correctly — check the marked answer and the wording.";
        }
        return "Discrimination is low: strong and weak learners perform about equally, "
                + "which usually means the wording is the obstacle rather than the concept.";
    }

    /**
     * Ownership check.
     *
     * <p>Enforced here rather than at the URL, because the rule is about the
     * resource rather than the route: an instructor may edit items, but only
     * their own. Role alone cannot express that, and checking it in the
     * controller would mean every future caller has to remember to repeat it.
     */
    private Item requireOwned(Long itemId, Long userId, boolean isAdmin) {
        Item item = items.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Item", itemId));
        if (!isAdmin && !userId.equals(item.getAuthorId())) {
            throw new ForbiddenException("You can only modify items you authored.");
        }
        return item;
    }

    private ItemDtos.ItemResponse toResponse(Item item) {
        List<ItemOption> opts = options.findByItemIdOrderByPosition(item.getId());
        Map<Long, String> names = misconceptions.findAll().stream()
                .collect(Collectors.toMap(Misconception::getId, Misconception::getName,
                        (a, b) -> a));

        return new ItemDtos.ItemResponse(
                item.getId(), item.getSkillId(), item.getStem(), item.getType(),
                item.getExplanation(), item.getDeclaredDifficulty(), item.effectiveDifficulty(),
                item.isCalibrated(), item.getTimesSeen(), item.pValue(), item.getStatus(),
                opts.stream().map(option -> new ItemDtos.OptionResponse(
                        option.getId(), option.getText(), option.isCorrect(),
                        option.getMisconceptionId(),
                        option.getMisconceptionId() == null ? null
                                : names.get(option.getMisconceptionId()),
                        option.getPosition())).toList());
    }

    private static <T> Function<T, T> identity() {
        return t -> t;
    }
}
