package com.skillsphere.skill;

/**
 * How a response changes what the platform believes about a learner.
 *
 * <p>Part of the skill module's public API. Assessment observes responses and
 * owns the items; skill owns the learner model and is the only module allowed to
 * change it. Without this seam, assessment would reach into
 * {@code skill.domain.LearnerSkillState} directly — which the boundary test
 * rejects, and rightly: mastery is the number a credential rests on, and exactly
 * one module should be able to move it.
 *
 * <p><b>The awkward part, made explicit.</b> A single response updates state on
 * both sides: the learner's ability and mastery belong to skill, while the item's
 * Elo rating and response counts belong to assessment. Rather than have one
 * module write the other's tables, {@link #recordResponse} updates the learner
 * and <em>returns</em> the item's new rating for assessment to persist. Each
 * module writes only what it owns.
 *
 * <p>That is also precisely the seam this becomes in Sprint 6: the call turns
 * into a message, the return value into a response, and neither side changes.
 */
public interface MasteryUpdater {

    /**
     * Everything about a response that the learner model needs.
     *
     * <p>Item parameters are passed in rather than looked up, because assessment
     * owns them and skill must not read assessment's tables — the same rule in
     * the other direction.
     *
     * @param itemDifficulty     the difficulty the engine actually routed on:
     *                           the author's declared prior until the item is
     *                           calibrated, the learned value afterwards
     * @param optionCount        drives the guess probability. Four options means
     *                           a blind guess succeeds a quarter of the time, so
     *                           a correct answer is correspondingly weaker
     *                           evidence
     */
    record ResponseOutcome(
            Long userId,
            Long skillId,
            boolean correct,
            double itemDifficulty,
            double itemDiscrimination,
            int itemEloRating,
            int optionCount) {
    }

    /**
     * @param newItemEloRating  returned so assessment can persist it. This is how
     *                          the item bank calibrates itself: the item concedes
     *                          exactly what the learner gains
     * @param predictedCorrect  P(correct) before the answer was known. Retained
     *                          because it is what explains the size of the
     *                          update afterwards — the evidence behind the
     *                          number, not just the number
     * @param masteryJustReached true only on the response that crossed the
     *                          threshold, so a consumer can react once rather
     *                          than on every subsequent response
     */
    record MasteryResult(
            double theta,
            double standardError,
            double masteryProbability,
            double previousMastery,
            int learnerEloRating,
            int newItemEloRating,
            double predictedCorrect,
            boolean masteryJustReached) {

        /** How far the estimate moved — the visible part of a response landing. */
        public double masteryDelta() {
            return masteryProbability - previousMastery;
        }
    }

    /**
     * Applies one response to the learner model.
     *
     * <p>Updates ability, uncertainty, mastery and Elo together, because they are
     * three views of the same event and letting them drift apart would mean the
     * passport and the routing disagreed about the same learner.
     */
    MasteryResult recordResponse(ResponseOutcome outcome);

    /** Current belief about one learner and one skill, without changing anything. */
    LearnerState currentState(Long userId, Long skillId);

    /**
     * @param responseCount how much evidence the estimate rests on. Shown
     *                      alongside mastery because 0.85 from three responses
     *                      and 0.85 from forty are not the same claim
     */
    record LearnerState(
            Long skillId,
            double theta,
            double standardError,
            double masteryProbability,
            int eloRating,
            int responseCount,
            boolean mastered) {
    }
}
