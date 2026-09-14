package com.skillsphere.skill.web;

import com.skillsphere.shared.domain.LevelBand;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Request and response shapes for the skill graph. */
public final class SkillDtos {

    private SkillDtos() {
    }

    /**
     * @param slug stable, URL-safe, and never regenerated from the name. Renaming
     *             "OOP" to "Object-Oriented Programming" must not break links
     *             already shared, evidence already issued, or a passport an
     *             employer has bookmarked.
     */
    public record CreateSkillRequest(
            @NotBlank @Size(max = 120)
            @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                     message = "Slug must be lowercase words separated by hyphens")
            String slug,

            @NotBlank @Size(max = 150) String name,
            String description,
            Long categoryId,
            LevelBand levelBand,

            @Min(1) Integer estMinutes,

            /**
             * Daily decay rate. Zero marks the skill as durable and exempts it
             * from refresher prompts entirely — correct for things nobody
             * forgets, and important because nagging someone about knowledge
             * that has not faded teaches them to ignore every prompt.
             */
            @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal decayRate) {
    }

    public record UpdateSkillRequest(
            @NotBlank @Size(max = 150) String name,
            String description,
            Long categoryId,
            LevelBand levelBand,
            @Min(1) Integer estMinutes,
            @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal decayRate,
            Boolean active) {
    }

    /**
     * @param strength 1.00 is a hard gate that blocks progression; anything lower
     *                 advises an ordering without blocking. Modelling both is what
     *                 stops the graph collapsing into one rigid sequence.
     */
    public record AddPrerequisiteRequest(
            @NotNull Long prerequisiteSkillId,
            @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("1.0")
            BigDecimal strength) {

        public AddPrerequisiteRequest {
            if (strength == null) {
                strength = BigDecimal.ONE;
            }
        }
    }

    public record SkillResponse(
            Long id,
            String slug,
            String name,
            String description,
            Long categoryId,
            String categoryName,
            LevelBand levelBand,
            int estMinutes,
            BigDecimal decayRate,
            boolean active) {
    }

    /**
     * A skill as it appears to one learner.
     *
     * <p>{@code blockedBy} is the field that matters. Telling someone a skill is
     * locked is useless on its own; naming the prerequisites still outstanding
     * turns it into an instruction. It is also the raw material for the
     * explainability feature — the same data that answers "why this next?".
     */
    public record LearnerSkillResponse(
            Long id,
            String slug,
            String name,
            LevelBand levelBand,
            int estMinutes,
            BigDecimal masteryProbability,
            boolean mastered,
            boolean available,
            List<SkillRef> blockedBy) {
    }

    public record SkillRef(Long id, String name) {
    }

    public record PrerequisiteResponse(
            Long id,
            SkillRef skill,
            SkillRef prerequisite,
            BigDecimal strength,
            boolean hardGate) {
    }

    /** A skill plus its immediate neighbours, for the graph editor. */
    public record SkillGraphNode(
            SkillResponse skill,
            List<SkillRef> directPrerequisites,
            List<SkillRef> directUnlocks) {
    }
}
