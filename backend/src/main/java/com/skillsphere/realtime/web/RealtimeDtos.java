package com.skillsphere.realtime.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public final class RealtimeDtos {

    private RealtimeDtos() {
    }

    public record CreateArenaRequest(
            @NotBlank String title,
            @NotNull Long skillId,
            @Min(3) @Max(30) int questionCount,
            @Min(5) @Max(120) int secondsPerQuestion) {
    }

    public record JoinRequest(String guestToken, String displayName) {
    }

    public record AnswerRequest(
            @NotNull Long participantId,
            @NotNull Long arenaQuestionId,
            Long selectedOptionId,
            @Positive Integer responseTimeMs) {
    }
}
