package com.skillsphere.shared.psychometrics;

/**
 * Elo ratings, applied to learners and assessment items rather than chess players.
 *
 * <p><b>Why Elo at all, when IRT is the better model.</b> Item Response Theory
 * needs calibrated item parameters before it means anything, and calibration
 * needs a corpus of responses — so a new platform has a measurement system that
 * cannot work until it has already been used. Elo has no such requirement: it
 * produces a sensible estimate from the very first answer and improves
 * monotonically after that. It is what carries the platform across the cold
 * start while IRT gathers the data it needs.
 *
 * <p><b>The insight that makes it work here.</b> A learner answering an item is
 * structurally identical to two players facing each other: one side wins. So the
 * learner's rating rises when they answer correctly, and — the part people
 * forget — <em>the item's rating falls</em>. Both sides update. Over thousands of
 * responses the item bank calibrates itself for free, with no separate
 * calibration run, because every response is simultaneously a measurement of the
 * learner and of the item.
 *
 * <p>Stateless and pure by design: these are functions of their inputs alone, so
 * they can be tested by hand-calculation with no database, no Spring context and
 * no mocking.
 */
public final class EloRating {

    private EloRating() {
    }

    /** Rating everyone and everything starts at. Arbitrary but conventional. */
    public static final int INITIAL_RATING = 1200;

    /**
     * The scale constant. 400 means a 400-point gap implies roughly a 10:1
     * expectation of winning — inherited from chess and kept because the
     * resulting numbers are familiar and human-readable.
     */
    private static final double SCALE = 400.0;

    /**
     * Probability that a learner of this rating answers an item of that rating
     * correctly.
     *
     * <p>The logistic curve: equal ratings give 0.5, and the odds shift tenfold
     * for every 400 points of difference.
     */
    public static double expectedScore(double learnerRating, double itemRating) {
        return 1.0 / (1.0 + Math.pow(10.0, (itemRating - learnerRating) / SCALE));
    }

    /**
     * The learner's new rating after answering.
     *
     * <p>Movement is proportional to surprise. Beating a much harder item moves
     * the rating a long way; answering something far below your level barely
     * registers. That property is the entire reason this works as a measurement
     * rather than a score — it rewards evidence, not activity.
     *
     * @param kFactor how fast ratings move. Higher reacts quickly but is noisy;
     *                lower is stable but slow to recognise genuine improvement.
     */
    public static double updateLearnerRating(double learnerRating, double itemRating,
                                             boolean correct, double kFactor) {
        double expected = expectedScore(learnerRating, itemRating);
        double actual = correct ? 1.0 : 0.0;
        return learnerRating + kFactor * (actual - expected);
    }

    /**
     * The item's new rating after being answered.
     *
     * <p>Deliberately the mirror image: the item loses exactly what the learner
     * gains. An item that strong learners keep failing drifts upward and is
     * recognised as hard; one that weak learners keep passing drifts down. This
     * is how the bank calibrates itself without anyone running a calibration
     * study — and it is why the two updates must use the same expected value,
     * computed once before either side moves.
     */
    public static double updateItemRating(double itemRating, double learnerRating,
                                          boolean correct, double kFactor) {
        double expectedForLearner = expectedScore(learnerRating, itemRating);
        double actualForLearner = correct ? 1.0 : 0.0;
        // The item's outcome is the complement of the learner's.
        return itemRating + kFactor * (expectedForLearner - actualForLearner);
    }

    /**
     * Both updates computed together from a single snapshot.
     *
     * <p>Provided because applying them in sequence is a real bug: updating the
     * learner first and then computing the item's move from the <em>new</em>
     * learner rating uses information from the future. The two sides must move
     * against the same expectation, so they are calculated before either is
     * applied.
     */
    public static Update apply(double learnerRating, double itemRating,
                               boolean correct, double kFactor) {
        double expected = expectedScore(learnerRating, itemRating);
        double actual = correct ? 1.0 : 0.0;
        double delta = kFactor * (actual - expected);

        return new Update(
                learnerRating + delta,
                itemRating - delta,
                expected);
    }

    /**
     * A K-factor that falls as evidence accumulates.
     *
     * <p>A learner with three responses should move quickly — the estimate is
     * mostly guesswork and needs to find its level. One with two hundred should
     * move slowly, because a single unlucky answer must not undo a
     * well-established rating. A fixed K gives up one property or the other.
     */
    public static double adaptiveKFactor(int responseCount, double baseK) {
        if (responseCount < 10) {
            return baseK * 2.0;      // find the level fast
        }
        if (responseCount < 30) {
            return baseK;
        }
        return baseK * 0.5;          // settle
    }

    /**
     * @param learnerRating new learner rating
     * @param itemRating    new item rating
     * @param expected      the probability used for both, kept for auditing —
     *                      it is the number that explains why the ratings moved
     *                      as far as they did
     */
    public record Update(double learnerRating, double itemRating, double expected) {
    }
}
