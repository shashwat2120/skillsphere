package com.skillsphere.assessment.web;

import com.skillsphere.assessment.internal.DiagnosticService;
import com.skillsphere.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The adaptive diagnostic.
 *
 * <p>Two endpoints, because the loop only has two moves: begin, and answer. The
 * learner id always comes from the security context rather than the request —
 * accepting it as a parameter would let anyone submit answers that move another
 * learner's mastery, which is the whole credential compromised by one field.
 */
@RestController
@RequestMapping("/api/diagnostics")
@RequiredArgsConstructor
@Tag(name = "Adaptive diagnostic",
     description = "Item selection by information gain, with misconception feedback")
public class DiagnosticController {

    private final DiagnosticService diagnosticService;

    @PostMapping("/start")
    @Operation(summary = "Start or resume a diagnostic",
            description = "Resumes an assessment already in progress for the same skill rather than "
                    + "restarting it — a dropped connection should not discard honest answers. "
                    + "Returns the first question immediately.")
    public DiagnosticDtos.NextItemResponse start(@RequestParam Long skillId) {
        return diagnosticService.startOrResume(CurrentUser.requireId(), skillId);
    }

    @PostMapping("/{assessmentId}/answer")
    @Operation(summary = "Submit an answer and receive the next question",
            description = "Scores the response, updates ability and mastery, and selects the next "
                    + "item in one call — the estimate produced by this answer is what chooses the "
                    + "next question, so they cannot be separate requests. A wrong answer returns "
                    + "the specific misconception behind the option chosen, not just 'incorrect'. "
                    + "The test ends when further questions would not change the estimate.")
    public DiagnosticDtos.AnswerResponse answer(
            @PathVariable Long assessmentId,
            @Valid @RequestBody DiagnosticDtos.SubmitAnswerRequest request) {
        return diagnosticService.submitAnswer(assessmentId, CurrentUser.requireId(), request);
    }
}
