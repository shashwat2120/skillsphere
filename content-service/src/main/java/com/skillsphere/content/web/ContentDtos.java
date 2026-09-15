package com.skillsphere.content.web;

import com.skillsphere.content.domain.CourseStatus;
import com.skillsphere.content.domain.LessonType;
import com.skillsphere.shared.domain.LevelBand;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ContentDtos {

    private ContentDtos() {
    }

    public record CreateCourseRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 220)
            @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                     message = "Slug must be lowercase words separated by hyphens")
            String slug,
            @Size(max = 300) String subtitle,
            String description,
            Long categoryId,
            LevelBand levelBand) {
    }

    public record CreateModuleRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 500) String summary) {
    }

    /**
     * @param videoUrl an embed URL. Video is never hosted by this platform —
     *                 storage, bandwidth and transcoding are the only part of
     *                 this product that costs real money, and serving the bytes
     *                 is not what differentiates it.
     */
    public record CreateLessonRequest(
            @NotBlank @Size(max = 200) String title,
            LessonType type,
            String content,
            @Size(max = 500) String videoUrl,
            @Size(max = 500) String resourceUrl,
            Integer durationSeconds,
            Boolean preview) {
    }

    /**
     * Tags a lesson with a skill it teaches.
     *
     * <p>The single most consequential call in this module. An untagged lesson is
     * invisible to the path engine: it can be read by someone browsing a course,
     * but the engine will never recommend it, because it cannot say which gap the
     * lesson would close.
     *
     * @param weight how much of the lesson is about this skill. A passing mention
     *               and the main subject must not rank equally, or learners get
     *               sent to material that barely addresses their gap.
     */
    public record TagSkillRequest(
            @NotNull Long skillId,
            @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("1.0")
            BigDecimal weight) {

        public TagSkillRequest {
            if (weight == null) {
                weight = BigDecimal.ONE;
            }
        }
    }

    public record CourseResponse(
            Long id,
            String title,
            String slug,
            String subtitle,
            String description,
            Long instructorId,
            LevelBand levelBand,
            CourseStatus status,
            int estMinutes,
            Instant publishedAt,
            int moduleCount) {
    }

    public record ModuleResponse(
            Long id,
            String title,
            String summary,
            int position,
            List<LessonResponse> lessons) {
    }

    public record LessonResponse(
            Long id,
            String title,
            LessonType type,
            String content,
            String videoUrl,
            String resourceUrl,
            int durationSeconds,
            int position,
            boolean preview,
            /** The skills this lesson teaches — what makes it reachable by the engine. */
            List<SkillTag> skills) {
    }

    public record SkillTag(Long skillId, String skillName, BigDecimal weight) {
    }

    /** A course with its full structure, for the builder and the learner view. */
    public record CourseDetailResponse(
            CourseResponse course,
            List<ModuleResponse> modules) {
    }
}
