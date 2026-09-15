package com.skillsphere.assessment.web;

import com.skillsphere.assessment.domain.ItemType;
import com.skillsphere.assessment.domain.TerminationReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public final class DiagnosticDtos {

    private DiagnosticDtos() {
    }

    public record SubmitAnswerRequest(
            @NotNull Long itemId,
            @NotNull Long optionId,
            /** Client-reported, used only as a diagnostic signal — never for scoring. */
            @Positive Integer responseTimeMs) {
    }

    /**
     * A question as the learner sees it.
     *
     * <p>Note what is absent: there is no correct flag on any option, and no
     * difficulty, explanation or misconception. Everything the client does not
     * strictly need to render the question stays on the server, because an
     * assessment API that ships the answer key is defeated by opening developer
     * tools.
     */
    public record ItemView(Long id, String stem, ItemType type, List<OptionView> options) {
    }

    public record OptionView(Long id, String text) {
    }

    /**
     * @param confidence how close the estimate is to being precise enough, 0–1.
     *                   Shown instead of a question counter because an adaptive
     *                   test has no fixed length — "question 6 of 25" would
     *                   promise nineteen more that may never be asked.
     */
    public record NextItemResponse(
            Long assessmentId,
            boolean finished,
            ItemView item,
            int itemsServed,
            int maxItems,
            double confidence,
            DiagnosticResult result) {
    }

    /**
     * What the learner is told after answering.
     *
     * <p>{@code misconception} is the field that makes this product different. A
     * quiz says "wrong". This says which belief produced the answer, and
     * {@code remediation} says what to do about it.
     */
    public record Feedback(
            boolean correct,
            String explanation,
            String misconception,
            String remediation) {
    }

    /** How the response moved the learner model — the visible part of it landing. */
    public record Progress(
            double masteryAfter,
            double masteryBefore,
            double masteryDelta,
            double ability,
            double standardError,
            boolean masteryJustReached) {
    }

    public record AnswerResponse(
            Feedback feedback,
            NextItemResponse next,
            Progress progress) {
    }

    /**
     * @param reason      why it stopped — reported because it changes what the
     *                    result means. Stopping on confidence produced a
     *                    trustworthy estimate; running out of items produced
     *                    whatever could be gathered
     * @param explanation the same thing in plain language, because a test that
     *                    ends after eight questions looks broken unless the
     *                    learner is told it ended early on purpose
     */
    public record DiagnosticResult(
            Long skillId,
            String skillName,
            double ability,
            double standardError,
            double mastery,
            boolean mastered,
            int itemsServed,
            int itemsCorrect,
            TerminationReason reason,
            String explanation) {
    }
}
