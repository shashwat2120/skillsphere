package com.skillsphere.shared.psychometrics;

/**
 * Bayesian Knowledge Tracing — the model behind every number on the passport.
 *
 * <p><b>The question it answers, and why it is a different question.</b> Item
 * Response Theory estimates <em>ability</em>: where a learner sits on a scale.
 * BKT estimates <em>mastery</em>: the probability they have actually learned the
 * skill. Those are not the same claim, and only the second one belongs on a
 * credential an employer might read.
 *
 * <p>So when the passport says 91%, it does not mean "answered 91% correctly". It
 * means <b>P(mastered) = 0.91 given this response history</b> — a probability
 * with a derivation, which is the only kind of number that survives being
 * questioned.
 *
 * <p><b>Four parameters, and the two that make it honest:</b>
 * <ul>
 *   <li><b>P(L₀) — prior.</b> Probability of already knowing it before any
 *       evidence.</li>
 *   <li><b>P(T) — transit.</b> Probability of learning it from one attempt, which
 *       is what lets mastery rise from practice rather than only from being
 *       measured.</li>
 *   <li><b>P(G) — guess.</b> Probability of answering correctly <em>without</em>
 *       knowing. On four-option multiple choice this is about 0.25 by
 *       construction, and ignoring it is the single biggest reason naive
 *       quiz scores overstate what people know.</li>
 *   <li><b>P(S) — slip.</b> Probability of answering incorrectly <em>despite</em>
 *       knowing — a misread, a typo, a lapse. Without it, one careless mistake
 *       would erase a genuinely mastered skill.</li>
 * </ul>
 *
 * <p>Guess and slip are what separate this from counting right answers. A
 * percentage score treats a lucky guess and a confident answer identically, and
 * treats one slip as proof of ignorance. BKT treats both as what they are:
 * weak evidence.
 */
public final class BayesianKnowledgeTracing {

    private BayesianKnowledgeTracing() {
    }

    /**
     * Default starting belief.
     *
     * <p>Low on purpose. The platform's claim is that every skill assertion is
     * backed by evidence, so a learner begins assumed <em>not</em> to know
     * something and earns the assertion. Starting high would mean the passport
     * asserts competence the platform has never observed.
     */
    public static final double DEFAULT_PRIOR = 0.05;

    /** Probability of learning from a single attempt. */
    public static final double DEFAULT_TRANSIT = 0.12;

    /** Slip: even a master occasionally misreads the question. */
    public static final double DEFAULT_SLIP = 0.08;

    /** Numerical guard — probabilities never reach exactly 0 or 1. */
    private static final double EPSILON = 1e-6;

    /**
     * Guess probability implied by the number of options.
     *
     * <p>Derived rather than estimated, which is unusual and deliberate: the
     * usual approach fits P(G) from response data, and that data does not exist
     * on a new platform. For multiple choice the value is structural — four
     * options means a blind guess succeeds a quarter of the time — so it can be
     * known from day one with no corpus at all.
     *
     * <p>A floor of 0.05 is applied because even free-text answers are
     * occasionally right by accident.
     */
    public static double guessFromOptionCount(int optionCount) {
        if (optionCount <= 1) {
            return 0.05;
        }
        return Math.max(0.05, 1.0 / optionCount);
    }

    /**
     * Updates mastery from one observed response.
     *
     * <p>Two steps, and conflating them is the classic implementation error.
     *
     * <p><b>1. Evidence.</b> Bayes' rule, applied to what was just observed:
     * <pre>
     *   correct:    P(L|obs) = P(L)(1−S) / [ P(L)(1−S) + (1−P(L))·G ]
     *   incorrect:  P(L|obs) = P(L)·S    / [ P(L)·S    + (1−P(L))(1−G) ]
     * </pre>
     * This asks only "given what I just saw, how likely is it they already knew
     * it?" — it contains no notion of learning.
     *
     * <p><b>2. Learning.</b> Attempting the question was itself a chance to learn:
     * <pre>
     *   P(L') = P(L|obs) + (1 − P(L|obs))·T
     * </pre>
     * Omitting this step produces a model where mastery can only ever be
     * revealed, never acquired — so a learner who genuinely improves through
     * practice is never credited for it.
     *
     * @param priorMastery current P(mastered), strictly between 0 and 1
     * @param correct      what was observed
     * @param guess        P(correct | not mastered)
     * @param slip         P(incorrect | mastered)
     * @param transit      P(learning it from this attempt)
     */
    public static double update(double priorMastery, boolean correct,
                                double guess, double slip, double transit) {
        double prior = clamp(priorMastery);

        double posterior;
        if (correct) {
            double knewAndGotIt = prior * (1.0 - slip);
            double guessedIt = (1.0 - prior) * guess;
            posterior = knewAndGotIt / (knewAndGotIt + guessedIt);
        } else {
            double knewButSlipped = prior * slip;
            double didNotKnow = (1.0 - prior) * (1.0 - guess);
            posterior = knewButSlipped / (knewButSlipped + didNotKnow);
        }

        // Step 2 — the attempt was also an opportunity to learn.
        double afterLearning = posterior + (1.0 - posterior) * transit;
        return clamp(afterLearning);
    }

    /** Convenience overload using the standard slip and transit values. */
    public static double update(double priorMastery, boolean correct, int optionCount) {
        return update(priorMastery, correct,
                guessFromOptionCount(optionCount), DEFAULT_SLIP, DEFAULT_TRANSIT);
    }

    /**
     * Applies decay to mastery that has not been practised.
     *
     * <p>Exponential, because forgetting is: most of the loss happens early and
     * then flattens. The alternative — a linear drop — would eventually take a
     * skill to zero, asserting that someone who once demonstrated competence now
     * knows nothing at all, which is plainly false.
     *
     * <p>Decay is floored at the prior rather than at zero for the same reason:
     * having learned something once leaves you better off than never having
     * encountered it.
     *
     * @param dailyDecayRate proportion lost per day; zero marks a durable skill
     */
    public static double decay(double mastery, long daysSincePractice, double dailyDecayRate) {
        if (dailyDecayRate <= 0 || daysSincePractice <= 0) {
            return mastery;
        }
        double decayed = mastery * Math.exp(-dailyDecayRate * daysSincePractice);
        return Math.max(DEFAULT_PRIOR, decayed);
    }

    /**
     * How many consecutive correct answers are still needed to reach a threshold.
     *
     * <p>Used to tell a learner what remains rather than only where they are:
     * "two more correct answers" is actionable in a way that "0.62" is not.
     */
    public static int responsesToReachThreshold(double currentMastery, double threshold,
                                                int optionCount) {
        double mastery = currentMastery;
        int count = 0;
        while (mastery < threshold && count < 100) {
            mastery = update(mastery, true, optionCount);
            count++;
        }
        return count;
    }

    private static double clamp(double probability) {
        return Math.max(EPSILON, Math.min(1.0 - EPSILON, probability));
    }
}
