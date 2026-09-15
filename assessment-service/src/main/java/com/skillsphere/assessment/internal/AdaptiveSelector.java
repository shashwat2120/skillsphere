package com.skillsphere.assessment.internal;

import com.skillsphere.assessment.domain.Item;
import com.skillsphere.assessment.domain.ItemRepository;
import com.skillsphere.shared.psychometrics.ItemResponseTheory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Chooses the next question — the component that makes this a diagnostic rather
 * than a quiz.
 *
 * <p><b>The rule, in one line:</b> ask the question whose answer is hardest to
 * predict.
 *
 * <p>A fixed test asks everyone the same twenty questions, most of which are
 * either obviously too easy or obviously too hard for any given person. Both
 * kinds waste the learner's time and tell the system nothing: if you are 95%
 * certain how someone will answer, their answer carries almost no information.
 * Fisher information formalises exactly that intuition and peaks where P(correct)
 * is 0.5 — at the learner's own current level.
 *
 * <p>So the selector does not walk a list. It computes, for every candidate,
 * how much that item would tell us about <em>this</em> learner right now, and
 * picks the best.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdaptiveSelector {

    /**
     * Randomisation window.
     *
     * <p>Pure argmax is the obvious implementation and it is wrong in practice.
     * Two learners of similar ability would receive an identical sequence, which
     * makes the test trivially shareable — one person posts the questions and the
     * next answers from memory. Worse, the single most informative item in the
     * bank would be served to nearly everybody and wear out immediately.
     *
     * <p>Selecting at random from the top few costs a negligible amount of
     * information and removes both problems. This is standard practice in
     * computerised adaptive testing, where it is called exposure control.
     */
    private static final int SELECTION_POOL = 3;

    private final ItemRepository items;
    private final Random random = new Random();

    /**
     * The most informative unseen item for this learner in this skill.
     *
     * <p>Returns empty when the bank is exhausted — a real condition on a small
     * bank, and one the caller must handle by ending the assessment rather than
     * repeating a question. Re-serving a seen item would let a learner memorise
     * answers and climb a measured ability they do not actually have, which is
     * the failure mode that quietly invalidates the whole passport.
     */
    @Transactional(readOnly = true)
    public Optional<Item> selectNext(Long skillId, Long userId, double currentTheta) {
        List<Item> candidates = items.findUnseenBySkill(skillId, userId);

        if (candidates.isEmpty()) {
            log.debug("No unseen items left for user {} in skill {}", userId, skillId);
            return Optional.empty();
        }

        List<Item> ranked = candidates.stream()
                .sorted(Comparator.comparingDouble(
                        (Item item) -> informationFor(item, currentTheta)).reversed())
                .limit(SELECTION_POOL)
                .toList();

        Item chosen = ranked.get(random.nextInt(ranked.size()));

        log.debug("Selected item {} for user {} at theta {} (information {})",
                chosen.getId(), userId, currentTheta, informationFor(chosen, currentTheta));

        return Optional.of(chosen);
    }

    /**
     * How much this item would tell us about a learner at this ability.
     *
     * <p>Uses {@link Item#effectiveDifficulty()}, which is the author's declared
     * prior until the item has enough responses to be calibrated and the learned
     * value afterwards. That fallback is what lets the selector work sensibly on
     * day one instead of requiring a corpus that cannot exist yet.
     */
    public double informationFor(Item item, double theta) {
        return ItemResponseTheory.information(
                theta,
                item.effectiveDifficulty().doubleValue(),
                item.getDiscriminationA().doubleValue());
    }

    /**
     * Whether there is anything left to ask.
     *
     * <p>Checked before starting so a learner is never shown an assessment that
     * ends after one question, which reads as a broken product rather than an
     * empty bank.
     */
    @Transactional(readOnly = true)
    public boolean hasItemsAvailable(Long skillId, Long userId) {
        return !items.findUnseenBySkill(skillId, userId).isEmpty();
    }
}
