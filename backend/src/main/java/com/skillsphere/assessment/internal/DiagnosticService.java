package com.skillsphere.assessment.internal;

import com.skillsphere.assessment.domain.*;
import com.skillsphere.assessment.web.DiagnosticDtos;
import com.skillsphere.shared.error.ForbiddenException;
import com.skillsphere.shared.error.NotFoundException;
import com.skillsphere.shared.error.ValidationException;
import com.skillsphere.shared.psychometrics.ItemResponseTheory;
import com.skillsphere.skill.MasteryUpdater;
import com.skillsphere.skill.SkillLookup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The adaptive loop.
 *
 * <p>One cycle: select the most informative unseen item, serve it, take the
 * answer, update ability and mastery, decide whether there is anything left
 * worth asking. Everything interesting is in the last step — knowing when to
 * stop is what separates an adaptive test from a shorter fixed one.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosticService {

    private final AssessmentRepository assessments;
    private final ResponseRepository responses;
    private final ItemRepository items;
    private final ItemOptionRepository options;
    private final MisconceptionRepository misconceptions;
    private final AdaptiveSelector selector;
    private final MasteryUpdater masteryUpdater;
    private final SkillLookup skillLookup;

    @Value("${skillsphere.adaptive.diagnostic-max-items:25}")
    private int maxItems;

    @Value("${skillsphere.adaptive.diagnostic-target-se:0.30}")
    private double targetStandardError;

    /**
     * Starts a diagnostic, or resumes one already in flight.
     *
     * <p>Resuming rather than restarting is not a convenience. A learner whose
     * connection drops nine questions in has given nine honest answers; throwing
     * them away asks them to prove the same things twice and makes the platform
     * feel unreliable at exactly the moment it should not.
     */
    @Transactional
    public DiagnosticDtos.NextItemResponse startOrResume(Long userId, Long skillId) {
        SkillLookup.SkillInfo skill = skillLookup.findById(skillId)
                .orElseThrow(() -> new NotFoundException("Skill", skillId));

        Optional<Assessment> existing = assessments
                .findByUserIdAndStatus(userId, AssessmentStatus.IN_PROGRESS);

        if (existing.isPresent() && skillId.equals(existing.get().getSkillId())) {
            log.debug("Resuming assessment {} for user {}", existing.get().getId(), userId);
            return serveNext(existing.get(), userId);
        }

        // A different skill was in progress. Abandoned rather than completed —
        // the responses still count as evidence, but the ability estimate was
        // never finished and must not be presented as if it were.
        existing.ifPresent(open -> {
            open.setStatus(AssessmentStatus.ABANDONED);
            open.setTerminationReason(TerminationReason.ABANDONED);
        });

        // Checked before creating anything: an assessment that ends after one
        // question reads as a broken product rather than an empty bank.
        if (!selector.hasItemsAvailable(skillId, userId)) {
            throw new ValidationException("NO_ITEMS_AVAILABLE",
                    "There are no unanswered questions left for " + skill.name() + ".");
        }

        Assessment assessment = assessments.save(
                new Assessment(userId, AssessmentType.DIAGNOSTIC, skillId));

        log.info("Started diagnostic {} for user {} on skill '{}'",
                assessment.getId(), userId, skill.name());

        return serveNext(assessment, userId);
    }

    /**
     * Records an answer and returns the next question, or the result.
     *
     * <p>The whole cycle happens here because the pieces are not separable: the
     * ability estimate produced by this response is what selects the next item,
     * so scoring and selection cannot be different requests without the client
     * holding state it has no business holding.
     */
    @Transactional
    public DiagnosticDtos.AnswerResponse submitAnswer(Long assessmentId, Long userId,
                                                      DiagnosticDtos.SubmitAnswerRequest request) {
        Assessment assessment = assessments.findById(assessmentId)
                .orElseThrow(() -> new NotFoundException("Assessment", assessmentId));

        // Ownership, not just authentication. Without it any learner could post
        // answers into somebody else's assessment and move their mastery.
        if (!assessment.getUserId().equals(userId)) {
            throw new ForbiddenException("That assessment belongs to another learner.");
        }
        if (!assessment.isActive()) {
            throw new ValidationException("ASSESSMENT_CLOSED", "This assessment has already finished.");
        }

        Item item = items.findById(request.itemId())
                .orElseThrow(() -> new NotFoundException("Item", request.itemId()));

        List<ItemOption> itemOptions = options.findByItemIdOrderByPosition(item.getId());
        ItemOption chosen = itemOptions.stream()
                .filter(option -> option.getId().equals(request.optionId()))
                .findFirst()
                // The option must belong to this item. Without the check a
                // learner could submit the id of a correct option from an
                // entirely different question.
                .orElseThrow(() -> new ValidationException("INVALID_OPTION",
                        "That option does not belong to this question."));

        boolean correct = chosen.isCorrect();

        MasteryUpdater.LearnerState before =
                masteryUpdater.currentState(userId, item.getSkillId());

        MasteryUpdater.MasteryResult result = masteryUpdater.recordResponse(
                new MasteryUpdater.ResponseOutcome(
                        userId, item.getSkillId(), correct,
                        item.effectiveDifficulty().doubleValue(),
                        item.getDiscriminationA().doubleValue(),
                        item.getEloRating(),
                        itemOptions.size()));

        // Each module writes only what it owns: skill moved the learner, and
        // assessment persists the item side of the same event.
        item.setEloRating(result.newItemEloRating());
        item.recordResponse(correct);
        chosen.setTimesChosen(chosen.getTimesChosen() + 1);

        Long misconceptionId = chosen.getMisconceptionId();
        if (misconceptionId != null) {
            misconceptions.findById(misconceptionId).ifPresent(Misconception::observe);
        }

        Response response = new Response();
        response.setUserId(userId);
        response.setItem(item);
        response.setAssessmentId(assessmentId);
        response.setSelectedOptionId(chosen.getId());
        response.setCorrect(correct);
        response.setResponseTimeMs(request.responseTimeMs());
        response.setAbilityBefore(BigDecimal.valueOf(before.theta()));
        response.setAbilityAfter(BigDecimal.valueOf(result.theta()));
        response.setMasteryBefore(BigDecimal.valueOf(result.previousMastery()));
        response.setMasteryAfter(BigDecimal.valueOf(result.masteryProbability()));
        response.setMisconceptionId(misconceptionId);
        responses.save(response);

        assessment.recordAnswer(correct);

        DiagnosticDtos.Feedback feedback = buildFeedback(item, chosen, correct, misconceptionId);
        DiagnosticDtos.NextItemResponse next = serveNext(assessment, userId);

        return new DiagnosticDtos.AnswerResponse(feedback, next, toProgress(result));
    }

    /**
     * Serves the next item, or ends the assessment.
     *
     * <p>Three stopping rules, in order of how good an ending they represent.
     *
     * <p><b>Confidence.</b> The standard error has fallen below target, so more
     * questions would not change the answer. This is the ending an adaptive test
     * exists to reach, and it is why one learner finishes in eight questions and
     * another needs twenty.
     *
     * <p><b>Item cap.</b> A hard limit. Without it an inconsistent learner could
     * be asked indefinitely, since their uncertainty never settles — and at that
     * point the test has stopped measuring and started grinding.
     *
     * <p><b>Bank exhausted.</b> Nothing unseen remains. A content problem rather
     * than a learner one, recorded distinctly so it is not mistaken for a
     * confident result.
     */
    private DiagnosticDtos.NextItemResponse serveNext(Assessment assessment, Long userId) {
        MasteryUpdater.LearnerState state =
                masteryUpdater.currentState(userId, assessment.getSkillId());

        if (assessment.getItemsServed() > 0
                && ItemResponseTheory.isPreciseEnough(state.standardError(), targetStandardError)) {
            return complete(assessment, TerminationReason.CONFIDENCE_REACHED, state);
        }

        if (assessment.getItemsServed() >= maxItems) {
            return complete(assessment, TerminationReason.ITEM_CAP, state);
        }

        Optional<Item> nextItem =
                selector.selectNext(assessment.getSkillId(), userId, state.theta());

        if (nextItem.isEmpty()) {
            return complete(assessment, TerminationReason.NO_ITEMS_AVAILABLE, state);
        }

        Item item = nextItem.get();
        assessment.recordServed();

        List<DiagnosticDtos.OptionView> optionViews =
                options.findByItemIdOrderByPosition(item.getId()).stream()
                        // Sorted by id rather than by correctness, and the
                        // correct flag is never sent. Leaking it in the payload
                        // is the most common way an assessment API is defeated.
                        .sorted(Comparator.comparing(ItemOption::getPosition))
                        .map(option -> new DiagnosticDtos.OptionView(option.getId(), option.getText()))
                        .toList();

        return new DiagnosticDtos.NextItemResponse(
                assessment.getId(),
                false,
                new DiagnosticDtos.ItemView(item.getId(), item.getStem(), item.getType(), optionViews),
                assessment.getItemsServed(),
                maxItems,
                // Shown as a confidence bar: how close the estimate is to being
                // precise enough. Far more honest than a question counter, which
                // implies a fixed length the test does not have.
                confidenceFrom(state.standardError()),
                null);
    }

    private DiagnosticDtos.NextItemResponse complete(Assessment assessment,
                                                     TerminationReason reason,
                                                     MasteryUpdater.LearnerState state) {
        assessment.complete(reason);
        log.info("Diagnostic {} finished after {} items — {}",
                assessment.getId(), assessment.getItemsServed(), reason);

        return new DiagnosticDtos.NextItemResponse(
                assessment.getId(), true, null,
                assessment.getItemsServed(), maxItems,
                confidenceFrom(state.standardError()),
                new DiagnosticDtos.DiagnosticResult(
                        assessment.getSkillId(),
                        skillLookup.findById(assessment.getSkillId())
                                .map(SkillLookup.SkillInfo::name).orElse("Unknown"),
                        round(state.theta()),
                        round(state.standardError()),
                        round(state.masteryProbability()),
                        state.mastered(),
                        assessment.getItemsServed(),
                        assessment.getItemsCorrect(),
                        reason,
                        explain(reason, assessment.getItemsServed())));
    }

    /**
     * Says in plain language why the test stopped.
     *
     * <p>Worth the effort: a test that ends after eight questions looks broken
     * unless the learner is told it ended because it already knows the answer.
     */
    private String explain(TerminationReason reason, int itemsServed) {
        return switch (reason) {
            case CONFIDENCE_REACHED -> "Stopped after %d questions — further questions would not change the estimate."
                    .formatted(itemsServed);
            case ITEM_CAP -> "Reached the question limit. The estimate is usable but less precise than usual.";
            case NO_ITEMS_AVAILABLE -> "You have answered every available question for this skill.";
            case TIME_LIMIT -> "Time limit reached.";
            case ABANDONED -> "This assessment was left unfinished.";
        };
    }

    private DiagnosticDtos.Feedback buildFeedback(Item item, ItemOption chosen,
                                                  boolean correct, Long misconceptionId) {
        if (correct) {
            // Shown on correct answers too. Withholding it means a lucky guess
            // gets no correction and the platform records mastery it has not
            // actually established.
            return new DiagnosticDtos.Feedback(true, item.getExplanation(), null, null);
        }

        // The whole point of tagging distractors: not "wrong", but which belief
        // led here and what to do about it.
        return misconceptions.findById(misconceptionId == null ? -1L : misconceptionId)
                .map(m -> new DiagnosticDtos.Feedback(
                        false, item.getExplanation(), m.getName(), m.getRemediationHint()))
                .orElseGet(() -> new DiagnosticDtos.Feedback(
                        false, item.getExplanation(), null, null));
    }

    private DiagnosticDtos.Progress toProgress(MasteryUpdater.MasteryResult result) {
        return new DiagnosticDtos.Progress(
                round(result.masteryProbability()),
                round(result.previousMastery()),
                round(result.masteryDelta()),
                round(result.theta()),
                round(result.standardError()),
                result.masteryJustReached());
    }

    /** Standard error expressed as 0–1 confidence, for a progress indicator. */
    private double confidenceFrom(double standardError) {
        double confidence = 1.0 - (standardError - targetStandardError) / (1.0 - targetStandardError);
        return round(Math.max(0.0, Math.min(1.0, confidence)));
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
