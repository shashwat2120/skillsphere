package com.skillsphere.psychometrics;

import com.skillsphere.shared.psychometrics.BayesianKnowledgeTracing;
import com.skillsphere.shared.psychometrics.EloRating;
import com.skillsphere.shared.psychometrics.ItemResponseTheory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The heaviest-tested code in the project, and deliberately so.
 *
 * <p>Everything else fails loudly. A wrong Elo update throws nothing, returns a
 * plausible number, and writes it into a learner's skill profile — where it
 * eventually becomes a percentage on a credential an employer reads. Nobody
 * notices for months, and by then every estimate derived from it is also wrong.
 *
 * <p>So these tests do two things rather than one. Hand-calculated values pin the
 * exact arithmetic, and property tests pin the behaviour that must hold for
 * <em>every</em> input — a correct answer must never lower ability, uncertainty
 * must never grow after evidence, mastery must never exceed 1. The properties
 * catch the mistakes that hand-picked examples miss, because a formula can be
 * wrong in a way that happens to be right for the three values you chose.
 *
 * <p>No Spring context, no database, no mocks: these are pure functions, so the
 * whole suite runs in milliseconds and there is nowhere for a bug to hide.
 */
class PsychometricsTest {

    private static final double TOLERANCE = 1e-4;

    // =================================================================
    @Nested
    @DisplayName("Elo")
    class Elo {

        @Test
        @DisplayName("equal ratings give an even chance")
        void equalRatingsAreEven() {
            assertThat(EloRating.expectedScore(1200, 1200))
                    .isEqualTo(0.5, within(TOLERANCE));
        }

        @Test
        @DisplayName("a 400-point gap implies roughly ten-to-one")
        void fourHundredPointGap() {
            // The defining property of the 400 scale constant: ten times the odds.
            assertThat(EloRating.expectedScore(1600, 1200))
                    .isEqualTo(0.9091, within(0.001));
            assertThat(EloRating.expectedScore(1200, 1600))
                    .isEqualTo(0.0909, within(0.001));
        }

        @Test
        @DisplayName("beating a harder item moves the rating further than beating an easy one")
        void surpriseDrivesMovement() {
            double vsHard = EloRating.updateLearnerRating(1200, 1600, true, 32);
            double vsEasy = EloRating.updateLearnerRating(1200, 800, true, 32);

            // This is the property that makes Elo a measurement rather than a
            // score: it rewards evidence, not activity.
            assertThat(vsHard - 1200)
                    .as("an unlikely success should move the estimate a long way")
                    .isGreaterThan(vsEasy - 1200);
        }

        @Test
        @DisplayName("hand-calculated update")
        void exactArithmetic() {
            // expected = 1/(1+10^((1400-1200)/400)) = 1/(1+10^0.5) = 0.24025
            // new      = 1200 + 32 × (1 − 0.24025) = 1224.312
            assertThat(EloRating.updateLearnerRating(1200, 1400, true, 32))
                    .isEqualTo(1224.312, within(0.01));
        }

        @Test
        @DisplayName("the item loses exactly what the learner gains")
        void ratingsAreZeroSum() {
            EloRating.Update update = EloRating.apply(1200, 1400, true, 32);

            double learnerGain = update.learnerRating() - 1200;
            double itemLoss = 1400 - update.itemRating();

            // Conservation is what lets the bank calibrate itself: every point a
            // learner earns is a point the item concedes.
            assertThat(learnerGain).isEqualTo(itemLoss, within(TOLERANCE));
        }

        /**
         * Guards a real ordering bug. Updating the learner first and deriving the
         * item's move from the *new* rating uses information from the future, and
         * silently breaks conservation.
         */
        @Test
        @DisplayName("both sides update against the same pre-response expectation")
        void bothSidesUseTheSameSnapshot() {
            EloRating.Update combined = EloRating.apply(1200, 1400, true, 32);

            double separateLearner = EloRating.updateLearnerRating(1200, 1400, true, 32);
            double separateItem = EloRating.updateItemRating(1400, 1200, true, 32);

            assertThat(combined.learnerRating()).isEqualTo(separateLearner, within(TOLERANCE));
            assertThat(combined.itemRating()).isEqualTo(separateItem, within(TOLERANCE));
        }

        @Test
        @DisplayName("a correct answer never lowers the rating, at any gap")
        void correctNeverLowers() {
            for (int itemRating = 400; itemRating <= 2400; itemRating += 100) {
                double updated = EloRating.updateLearnerRating(1200, itemRating, true, 32);
                assertThat(updated)
                        .as("correct answer against item rated %d", itemRating)
                        .isGreaterThanOrEqualTo(1200);
            }
        }

        @Test
        @DisplayName("new learners move faster than established ones")
        void kFactorFallsWithEvidence() {
            double novice = EloRating.adaptiveKFactor(3, 32);
            double regular = EloRating.adaptiveKFactor(20, 32);
            double veteran = EloRating.adaptiveKFactor(200, 32);

            // A beginner needs to find their level quickly; someone with two
            // hundred responses must not be undone by one unlucky answer.
            assertThat(novice).isGreaterThan(regular);
            assertThat(regular).isGreaterThan(veteran);
        }
    }

    // =================================================================
    @Nested
    @DisplayName("Item Response Theory")
    class Irt {

        @Test
        @DisplayName("ability equal to difficulty gives exactly even odds")
        void abilityMatchesDifficulty() {
            // This is the definition of the difficulty parameter.
            assertThat(ItemResponseTheory.probabilityCorrect(1.0, 1.0, 1.5))
                    .isEqualTo(0.5, within(TOLERANCE));
        }

        @Test
        @DisplayName("hand-calculated 2PL probability")
        void exactProbability() {
            // P = 1/(1+e^(−1.2(0.5−0.0))) = 1/(1+e^−0.6) = 0.6457
            assertThat(ItemResponseTheory.probabilityCorrect(0.5, 0.0, 1.2))
                    .isEqualTo(0.6457, within(0.001));
        }

        @Test
        @DisplayName("higher discrimination sharpens the curve on both sides")
        void discriminationSharpens() {
            double sharpAbove = ItemResponseTheory.probabilityCorrect(1.0, 0.0, 2.5);
            double vagueAbove = ItemResponseTheory.probabilityCorrect(1.0, 0.0, 0.5);
            double sharpBelow = ItemResponseTheory.probabilityCorrect(-1.0, 0.0, 2.5);
            double vagueBelow = ItemResponseTheory.probabilityCorrect(-1.0, 0.0, 0.5);

            // A sharp item is a clean cut: clearly above and clearly below.
            assertThat(sharpAbove).isGreaterThan(vagueAbove);
            assertThat(sharpBelow).isLessThan(vagueBelow);
        }

        /**
         * The property the entire adaptive test rests on. If information did not
         * peak at the learner's own level, selecting the most informative item
         * would not select the most appropriate one.
         */
        @Test
        @DisplayName("information peaks where the outcome is least predictable")
        void informationPeaksAtAbility() {
            double atLevel = ItemResponseTheory.information(0.0, 0.0, 1.5);
            double tooEasy = ItemResponseTheory.information(0.0, -3.0, 1.5);
            double tooHard = ItemResponseTheory.information(0.0, 3.0, 1.5);

            assertThat(atLevel)
                    .as("a question they might get either way teaches the most")
                    .isGreaterThan(tooEasy)
                    .isGreaterThan(tooHard);
        }

        @Test
        @DisplayName("a question they are certain to pass teaches almost nothing")
        void certainOutcomesCarryNoInformation() {
            double certain = ItemResponseTheory.information(3.0, -3.0, 1.5);
            assertThat(certain)
                    .as("this is the argument against fixed-length tests")
                    .isLessThan(0.01);
        }

        @Test
        @DisplayName("every response reduces uncertainty")
        void standardErrorAlwaysFalls() {
            var afterCorrect = ItemResponseTheory.updateAbility(0.0, 1.0, 0.0, 1.2, true);
            var afterWrong = ItemResponseTheory.updateAbility(0.0, 1.0, 0.0, 1.2, false);

            // Evidence is evidence regardless of direction — being wrong is just
            // as informative as being right.
            assertThat(afterCorrect.standardError()).isLessThan(1.0);
            assertThat(afterWrong.standardError()).isLessThan(1.0);
        }

        @Test
        @DisplayName("a correct answer raises ability and a wrong one lowers it")
        void updatesMoveTheRightWay() {
            assertThat(ItemResponseTheory.updateAbility(0.0, 1.0, 0.0, 1.2, true).theta())
                    .isGreaterThan(0.0);
            assertThat(ItemResponseTheory.updateAbility(0.0, 1.0, 0.0, 1.2, false).theta())
                    .isLessThan(0.0);
        }

        /**
         * Guards against a plain Newton step, which has no brake and can throw
         * the estimate to an absurd value on one surprising answer.
         */
        @Test
        @DisplayName("a confident estimate resists a single surprising answer")
        void confidentEstimatesAreStable() {
            var confident = ItemResponseTheory.updateAbility(2.0, 0.2, -2.0, 1.5, false);
            var uncertain = ItemResponseTheory.updateAbility(2.0, 1.5, -2.0, 1.5, false);

            double confidentMove = Math.abs(confident.theta() - 2.0);
            double uncertainMove = Math.abs(uncertain.theta() - 2.0);

            assertThat(confidentMove)
                    .as("one odd answer must not undo an established estimate")
                    .isLessThan(uncertainMove);
        }

        @Test
        @DisplayName("ability stays within the usable range")
        void thetaIsClamped() {
            double theta = 0.0;
            double se = 1.0;
            // Twenty consecutive correct answers on trivially easy items.
            for (int i = 0; i < 20; i++) {
                var estimate = ItemResponseTheory.updateAbility(theta, se, -3.0, 2.0, true);
                theta = estimate.theta();
                se = estimate.standardError();
            }
            assertThat(theta).isLessThanOrEqualTo(ItemResponseTheory.MAX_THETA);
        }

        @Test
        @DisplayName("an adaptive test converges toward the learner's true level")
        void convergesTowardTruth() {
            // A learner who answers roughly three questions in four correctly on
            // items pitched at 0.5 — so their ability sits somewhat above that.
            double theta = 0.0;
            double se = 1.0;
            for (int i = 0; i < 12; i++) {
                boolean correct = i % 4 != 0;
                var estimate = ItemResponseTheory.updateAbility(theta, se, 0.5, 1.3, correct);
                theta = estimate.theta();
                se = estimate.standardError();
            }

            assertThat(theta).as("estimate should climb above the item difficulty").isGreaterThan(0.3);
            assertThat(se).as("uncertainty should fall substantially from 1.0").isLessThan(0.55);
        }

        /**
         * Establishes how long a diagnostic actually needs to run, which is a
         * product decision disguised as a maths one.
         *
         * <p>This test originally asserted that twelve responses would reach a
         * standard error of 0.4, and failed — correctly. The arithmetic says
         * otherwise: an item with discrimination 1.3 contributes at most
         * a²·P·(1−P) = 0.42 of information, so precision climbs from 1.0 by
         * roughly 0.3–0.4 per response, and reaching SE 0.4 needs precision 6.25.
         * That is around fifteen to eighteen items, not twelve.
         *
         * <p>The finding matters beyond the test: it is what justifies the
         * configured item cap of 25. Too low and the diagnostic stops before it
         * knows anything; too high and a learner is asked questions that no
         * longer change the answer.
         */
        @Test
        @DisplayName("reaching a standard error of 0.4 takes roughly fifteen to twenty responses")
        void measuresHowManyResponsesPrecisionNeeds() {
            double theta = 0.0;
            double se = 1.0;
            int responses = 0;

            while (!ItemResponseTheory.isPreciseEnough(se, 0.4) && responses < 60) {
                boolean correct = responses % 4 != 0;
                var estimate = ItemResponseTheory.updateAbility(theta, se, theta, 1.3, correct);
                theta = estimate.theta();
                se = estimate.standardError();
                responses++;
            }

            assertThat(responses)
                    .as("a diagnostic capped at 25 items must be able to converge inside that budget")
                    .isBetween(10, 25);
        }
    }

    // =================================================================
    @Nested
    @DisplayName("Bayesian Knowledge Tracing")
    class Bkt {

        @Test
        @DisplayName("guess probability comes from the option count")
        void guessIsStructural() {
            // Derived rather than fitted — knowable on day one with no data.
            assertThat(BayesianKnowledgeTracing.guessFromOptionCount(4))
                    .isEqualTo(0.25, within(TOLERANCE));
            assertThat(BayesianKnowledgeTracing.guessFromOptionCount(2))
                    .isEqualTo(0.5, within(TOLERANCE));
        }

        @Test
        @DisplayName("a correct answer raises mastery and a wrong one lowers it")
        void updatesMoveTheRightWay() {
            double start = 0.5;
            assertThat(BayesianKnowledgeTracing.update(start, true, 4)).isGreaterThan(start);
            assertThat(BayesianKnowledgeTracing.update(start, false, 4)).isLessThan(start);
        }

        /**
         * The property that separates BKT from counting right answers. One lucky
         * guess must not certify a skill.
         */
        @Test
        @DisplayName("a single correct answer cannot establish mastery")
        void oneAnswerIsNotProof() {
            double afterOne = BayesianKnowledgeTracing.update(
                    BayesianKnowledgeTracing.DEFAULT_PRIOR, true, 4);

            assertThat(afterOne)
                    .as("one answer on a four-option item is weak evidence")
                    .isLessThan(0.5);
        }

        @Test
        @DisplayName("mastery accumulates across consecutive correct answers")
        void evidenceAccumulates() {
            double mastery = BayesianKnowledgeTracing.DEFAULT_PRIOR;
            for (int i = 0; i < 8; i++) {
                mastery = BayesianKnowledgeTracing.update(mastery, true, 4);
            }
            assertThat(mastery)
                    .as("sustained correct performance should establish mastery")
                    .isGreaterThan(0.8);
        }

        /**
         * The counterpart property. Without a slip parameter, one careless
         * mistake would erase a genuinely mastered skill.
         */
        @Test
        @DisplayName("one slip does not erase established mastery")
        void slipIsForgiven() {
            double mastery = BayesianKnowledgeTracing.DEFAULT_PRIOR;
            for (int i = 0; i < 10; i++) {
                mastery = BayesianKnowledgeTracing.update(mastery, true, 4);
            }
            double established = mastery;

            double afterSlip = BayesianKnowledgeTracing.update(established, false, 4);

            assertThat(afterSlip).isLessThan(established);
            assertThat(afterSlip)
                    .as("a single mistake is evidence, not proof of ignorance")
                    .isGreaterThan(0.4);
        }

        @Test
        @DisplayName("fewer options make a correct answer stronger evidence")
        void guessingWeakensEvidence() {
            double fromFourOptions = BayesianKnowledgeTracing.update(0.3, true, 4);
            double fromTwoOptions = BayesianKnowledgeTracing.update(0.3, true, 2);

            // A coin-flip question proves less than a four-way one.
            assertThat(fromFourOptions).isGreaterThan(fromTwoOptions);
        }

        @Test
        @DisplayName("mastery stays a probability under any sequence")
        void staysWithinBounds() {
            double mastery = 0.5;
            for (int i = 0; i < 200; i++) {
                mastery = BayesianKnowledgeTracing.update(mastery, i % 3 != 0, 4);
                assertThat(mastery).isBetween(0.0, 1.0);
            }
        }

        @Test
        @DisplayName("decay erodes unused mastery but never to nothing")
        void decayHasAFloor() {
            double mastery = 0.9;

            assertThat(BayesianKnowledgeTracing.decay(mastery, 30, 0.002))
                    .isLessThan(mastery)
                    .isGreaterThan(0.5);

            // Having learned something once leaves you better off than never
            // having met it, however long ago that was.
            assertThat(BayesianKnowledgeTracing.decay(mastery, 100_000, 0.01))
                    .isGreaterThanOrEqualTo(BayesianKnowledgeTracing.DEFAULT_PRIOR);
        }

        @Test
        @DisplayName("a durable skill never decays")
        void zeroRateMeansDurable() {
            assertThat(BayesianKnowledgeTracing.decay(0.9, 5_000, 0.0))
                    .isEqualTo(0.9, within(TOLERANCE));
        }

        @Test
        @DisplayName("remaining responses can be projected for the learner")
        void projectsRemainingWork() {
            int needed = BayesianKnowledgeTracing.responsesToReachThreshold(0.45, 0.80, 4);

            // "Two more correct answers" is actionable; "0.45" is not.
            assertThat(needed).isPositive().isLessThan(15);

            double mastery = 0.45;
            for (int i = 0; i < needed; i++) {
                mastery = BayesianKnowledgeTracing.update(mastery, true, 4);
            }
            assertThat(mastery).isGreaterThanOrEqualTo(0.80);
        }
    }
}
