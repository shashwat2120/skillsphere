package com.skillsphere.verification.web;

import com.skillsphere.verification.domain.AiUsageExtent;
import com.skillsphere.verification.domain.AiUsagePurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public final class VerificationDtos {

    private VerificationDtos() {
    }

    public record ProjectSummary(Long id, String title, String slug, String description,
                                  String brief, String levelBand, int estMinutes,
                                  String rubric, boolean requiresViva, List<String> skillNames) {
    }

    public record SaveDraftRequest(@NotBlank String content) {
    }

    public record SubmitRequest(
            String toolUsed,
            @NotNull AiUsagePurpose purpose,
            @NotNull AiUsageExtent extent,
            String detail) {
    }

    public record SubmissionView(Long id, Long projectId, String status, int attemptNo,
                                  String content, int draftCount) {
    }

    public record AnswerRequest(@NotNull Long turnId, @NotBlank String answer) {
    }
}
