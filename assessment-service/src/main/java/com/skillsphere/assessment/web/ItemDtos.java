package com.skillsphere.assessment.web;

import com.skillsphere.assessment.domain.ItemStatus;
import com.skillsphere.assessment.domain.ItemType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public final class ItemDtos {

    private ItemDtos() {
    }

    /**
     * @param declaredDifficulty the author's estimate on a -3 to +3 scale, where
     *                           0 is average. This is a Bayesian prior, not a
     *                           label: it is what the engine routes on until
     *                           enough responses exist to learn the real value,
     *                           and it is the answer to the cold-start problem
     *                           that otherwise makes an adaptive engine unusable
     *                           on a new platform.
     */
    public record CreateItemRequest(
            @NotNull Long skillId,
            @NotBlank @Size(max = 4000) String stem,
            ItemType type,
            @Size(max = 4000) String explanation,

            @DecimalMin("-3.0") @DecimalMax("3.0") BigDecimal declaredDifficulty,

            @NotEmpty @Size(min = 2, max = 8) @Valid List<OptionInput> options) {
    }

    /**
     * @param misconceptionId the belief that makes this wrong option attractive.
     *                        Optional but strongly encouraged: without it the
     *                        learner is told they are wrong and the platform
     *                        learns nothing about why, which forfeits the one
     *                        capability that separates this from a quiz engine.
     *                        Must be null on the correct option.
     */
    public record OptionInput(
            @NotBlank @Size(max = 2000) String text,
            boolean correct,
            Long misconceptionId) {
    }

    public record ItemResponse(
            Long id,
            Long skillId,
            String stem,
            ItemType type,
            String explanation,
            BigDecimal declaredDifficulty,
            /** What the engine actually uses — the prior until calibrated, then the learned value. */
            BigDecimal effectiveDifficulty,
            boolean calibrated,
            int timesSeen,
            BigDecimal pValue,
            ItemStatus status,
            List<OptionResponse> options) {
    }

    public record OptionResponse(
            Long id,
            String text,
            boolean correct,
            Long misconceptionId,
            String misconceptionName,
            int position) {
    }

    /** Quality control the platform runs on its own item bank. */
    public record ItemHealthResponse(
            Long id,
            String stem,
            Long skillId,
            int timesSeen,
            BigDecimal pValue,
            BigDecimal discrimination,
            String diagnosis) {
    }

    public record CreateMisconceptionRequest(
            @NotNull Long skillId,
            /** Phrased as a belief — "Thinks HashMap preserves insertion order" — not as a topic. */
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            /** Mandatory: a diagnosis with no remedy leaves the learner no better off. */
            @NotBlank @Size(max = 2000) String remediationHint,
            Long remediationLessonId) {
    }

    public record MisconceptionResponse(
            Long id,
            Long skillId,
            String name,
            String description,
            String remediationHint,
            int timesObserved) {
    }
}
