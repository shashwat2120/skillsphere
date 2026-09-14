package com.skillsphere.skill.internal;

import com.skillsphere.shared.psychometrics.BayesianKnowledgeTracing;
import com.skillsphere.shared.psychometrics.EloRating;
import com.skillsphere.shared.psychometrics.ItemResponseTheory;
import com.skillsphere.skill.MasteryUpdater;
import com.skillsphere.skill.domain.LearnerSkillState;
import com.skillsphere.skill.domain.LearnerSkillStateRepository;
import com.skillsphere.skill.domain.Skill;
import com.skillsphere.skill.domain.SkillMasteryHistory;
import com.skillsphere.skill.domain.SkillMasteryHistoryRepository;
import com.skillsphere.skill.domain.SkillRepository;
import com.skillsphere.shared.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * The only place in the system where a learner's mastery changes.
 *
 * <p>Concentrating it here is deliberate. Mastery is the number a skill claim
 * rests on, and a second code path that could move it — a bulk import, an admin
 * override, a well-meaning shortcut in another module — is how a credential
 * quietly stops meaning anything. One writer, one audit trail.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MasteryUpdateService implements MasteryUpdater {

    private final LearnerSkillStateRepository states;
    private final SkillMasteryHistoryRepository history;
    private final SkillRepository skills;

    @Value("${skillsphere.adaptive.elo-k-factor:32}")
    private double baseKFactor;

    @Value("${skillsphere.adaptive.mastery-threshold:0.80}")
    private BigDecimal masteryThreshold;

    @Override
    @Transactional
    public MasteryResult recordResponse(ResponseOutcome outcome) {
        LearnerSkillState state = states
                .findByUserIdAndSkillId(outcome.userId(), outcome.skillId())
                .orElseGet(() -> createInitialState(outcome.userId(), outcome.skillId()));

        double previousMastery = state.getMasteryProbability().doubleValue();
        boolean wasMastered = previousMastery >= masteryThreshold.doubleValue();

        // --- Elo -------------------------------------------------------------
        // Both sides move against the same pre-response expectation. Applying
        // them in sequence would let the item's update use a learner rating that
        // already reflects this answer, which breaks conservation.
        double k = EloRating.adaptiveKFactor(state.getAttemptsCount(), baseKFactor);
        EloRating.Update elo = EloRating.apply(
                state.getEloRating(), outcome.itemEloRating(), outcome.correct(), k);

        // --- IRT -------------------------------------------------------------
        ItemResponseTheory.AbilityEstimate ability = ItemResponseTheory.updateAbility(
                state.getAbilityTheta().doubleValue(),
                state.getAbilitySe().doubleValue(),
                outcome.itemDifficulty(),
                outcome.itemDiscrimination(),
                outcome.correct());

        // --- BKT -------------------------------------------------------------
        double newMastery = BayesianKnowledgeTracing.update(
                previousMastery, outcome.correct(), outcome.optionCount());

        state.setEloRating((int) Math.round(elo.learnerRating()));
        state.setAbilityTheta(round(ability.theta(), 4));
        state.setAbilitySe(round(ability.standardError(), 4));
        state.updateMastery(round(newMastery, 4));
        state.recordPractice(outcome.correct());
        state.markMasteredIfNeeded(masteryThreshold);

        // Append-only. The current state is a cache of this ledger, and every
        // figure on a passport can be traced back through it.
        history.save(SkillMasteryHistory.of(
                outcome.userId(), state.getSkill(), state.getMasteryProbability(),
                state.getAbilityTheta(), "RESPONSE", null));

        boolean nowMastered = newMastery >= masteryThreshold.doubleValue();
        if (!wasMastered && nowMastered) {
            log.info("User {} reached mastery of skill {} ({} responses)",
                    outcome.userId(), outcome.skillId(), state.getAttemptsCount());
        }

        return new MasteryResult(
                ability.theta(),
                ability.standardError(),
                newMastery,
                previousMastery,
                state.getEloRating(),
                (int) Math.round(elo.itemRating()),
                ability.predicted(),
                !wasMastered && nowMastered);
    }

    @Override
    @Transactional(readOnly = true)
    public LearnerState currentState(Long userId, Long skillId) {
        return states.findByUserIdAndSkillId(userId, skillId)
                .map(state -> new LearnerState(
                        skillId,
                        state.getAbilityTheta().doubleValue(),
                        state.getAbilitySe().doubleValue(),
                        state.getMasteryProbability().doubleValue(),
                        state.getEloRating(),
                        state.getAttemptsCount(),
                        state.getMasteryProbability().compareTo(masteryThreshold) >= 0))
                // A learner who has never touched a skill has no row, and that is
                // correct — pre-creating one per learner per skill would write
                // hundreds of rows at signup for skills most people never attempt.
                .orElseGet(() -> new LearnerState(
                        skillId, 0.0, 1.0,
                        BayesianKnowledgeTracing.DEFAULT_PRIOR,
                        EloRating.INITIAL_RATING, 0, false));
    }

    private LearnerSkillState createInitialState(Long userId, Long skillId) {
        Skill skill = skills.findById(skillId)
                .orElseThrow(() -> new NotFoundException("Skill", skillId));
        LearnerSkillState state = new LearnerSkillState(userId, skill);
        state.setFirstSeenAt(Instant.now());
        return states.save(state);
    }

    private BigDecimal round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }
}
