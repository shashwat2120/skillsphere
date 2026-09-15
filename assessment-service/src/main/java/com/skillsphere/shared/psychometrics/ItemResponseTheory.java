package com.skillsphere.shared.psychometrics;

/**
 * Item Response Theory — the model that makes "76% ready" a defensible number.
 *
 * <p><b>The idea in one sentence.</b> A learner has an ability, an item has a
 * difficulty, and both live on the same scale, so the probability of a correct
 * answer is a function of the distance between them.
 *
 * <p>That shared scale is what a percentage score cannot give you. Scoring 8/10
 * means nothing without knowing which ten questions; ability of +1.2 means the
 * same thing whichever items produced it, which is why two learners who sat
 * completely different adaptive tests can still be compared.
 *
 * <p><b>The 2PL model.</b> Two parameters per item:
 * <ul>
 *   <li><b>b — difficulty.</b> The ability at which a learner has a 50% chance.
 *       Same scale as ability, so b = 1.2 means "this bites at ability 1.2".</li>
 *   <li><b>a — discrimination.</b> How sharply the item separates learners around
 *       that point. High a is a clean cut: below b almost everyone fails, above
 *       it almost everyone passes. Low a means strong and weak learners do about
 *       equally well — the item is measuring something other than the skill,
 *       usually the wording.</li>
 * </ul>
 *
 * <p>The 3PL model adds a guessing parameter. Deliberately not used: guessing is
 * handled where it actually belongs, in {@link BayesianKnowledgeTracing}, where
 * it can be derived from the number of options rather than estimated from data
 * we do not yet have.
 *
 * <p>Stateless and pure — every function here is testable by hand-calculation.
 */
public final class ItemResponseTheory {

    private ItemResponseTheory() {
    }

    /** Ability and difficulty are clamped to this range; beyond it the logistic is flat. */
    public static final double MIN_THETA = -4.0;
    public static final double MAX_THETA = 4.0;

    /**
     * Probability of a correct response under the 2PL model.
     *
     * <pre>  P(θ) = 1 / (1 + e^(−a(θ − b)))</pre>
     *
     * <p>At θ = b the result is exactly 0.5 — the definition of difficulty.
     */
    public static double probabilityCorrect(double theta, double difficulty, double discrimination) {
        double exponent = -discrimination * (theta - difficulty);
        return 1.0 / (1.0 + Math.exp(exponent));
    }

    /**
     * Fisher information — how much this item would tell us about this learner.
     *
     * <pre>  I(θ) = a² · P(θ) · (1 − P(θ))</pre>
     *
     * <p><b>This single function is what makes the test adaptive.</b> Information
     * peaks where P = 0.5, at the item's own difficulty, and collapses toward
     * zero as the outcome becomes predictable.
     *
     * <p>Which is the whole argument against fixed tests: asking something the
     * learner is 95% certain to answer correctly consumes their time and teaches
     * the system almost nothing. The most informative question is always the one
     * whose answer is hardest to predict.
     *
     * <p>Discrimination enters squared, so a sharp item is disproportionately
     * more valuable than a vague one at the same difficulty.
     */
    public static double information(double theta, double difficulty, double discrimination) {
        double p = probabilityCorrect(theta, difficulty, discrimination);
        return discrimination * discrimination * p * (1.0 - p);
    }

    /**
     * Updates the ability estimate from one response.
     *
     * <p>Implemented as a Bayesian update rather than by re-fitting the whole
     * response history. The prior is a normal centred on the current estimate
     * with variance SE², and the posterior precision is the sum of the prior's
     * and the item's information:
     *
     * <pre>
     *   precision' = 1/SE² + I(θ)
     *   SE'        = √(1 / precision')
     *   θ'         = θ + SE'² · a · (u − P(θ))
     * </pre>
     *
     * <p>Three properties fall out of this, and all three matter.
     *
     * <p><b>Self-limiting.</b> The step is scaled by the new variance, so a
     * confident estimate moves little and an uncertain one moves a lot. A plain
     * Newton step has no such brake and can diverge wildly on a single surprising
     * answer.
     *
     * <p><b>Surprise-proportional.</b> The term (u − P) is the error: answering
     * correctly when P was 0.9 barely moves anything, while doing so when P was
     * 0.2 moves a great deal. The estimate responds to evidence, not to activity.
     *
     * <p><b>SE falls automatically.</b> No separate bookkeeping is needed to know
     * how confident the estimate is, and that number is what tells an adaptive
     * test when it may stop.
     *
     * @param correct whether the learner answered correctly
     */
    public static AbilityEstimate updateAbility(double theta, double standardError,
                                                double difficulty, double discrimination,
                                                boolean correct) {
        double p = probabilityCorrect(theta, difficulty, discrimination);
        double observed = correct ? 1.0 : 0.0;

        double priorPrecision = 1.0 / (standardError * standardError);
        double itemInformation = discrimination * discrimination * p * (1.0 - p);
        double posteriorPrecision = priorPrecision + itemInformation;

        double posteriorVariance = 1.0 / posteriorPrecision;
        double newTheta = theta + posteriorVariance * discrimination * (observed - p);

        return new AbilityEstimate(
                clamp(newTheta),
                Math.sqrt(posteriorVariance),
                p);
    }

    /**
     * Whether the estimate is precise enough to stop asking.
     *
     * <p>The termination rule that makes an adaptive test adaptive in length as
     * well as content: a learner whose level becomes obvious after six questions
     * is not made to sit another fourteen, while an inconsistent one is probed
     * further. A fixed-length test is the same burden for everybody regardless of
     * how much it has already learned about them.
     */
    public static boolean isPreciseEnough(double standardError, double targetStandardError) {
        return standardError <= targetStandardError;
    }

    /**
     * Converts ability to a 0–1 figure for display.
     *
     * <p><b>Presentation only — never feed this back into the model.</b> Ability
     * is unbounded and additive; a percentage is neither. Using the display value
     * in a calculation is how a scale silently stops meaning anything.
     */
    public static double abilityToDisplayScale(double theta) {
        return 1.0 / (1.0 + Math.exp(-theta));
    }

    private static double clamp(double theta) {
        return Math.max(MIN_THETA, Math.min(MAX_THETA, theta));
    }

    /**
     * @param theta         updated ability
     * @param standardError updated uncertainty — always lower than before, since
     *                      every response adds information
     * @param predicted     P(correct) before the answer was known, retained so
     *                      the size of the update can be explained afterwards
     */
    public record AbilityEstimate(double theta, double standardError, double predicted) {
    }
}
