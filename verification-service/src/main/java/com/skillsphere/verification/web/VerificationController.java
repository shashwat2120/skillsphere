package com.skillsphere.verification.web;

import com.skillsphere.shared.error.NotFoundException;
import com.skillsphere.shared.security.CurrentUser;
import com.skillsphere.verification.domain.Project;
import com.skillsphere.verification.domain.ProjectRepository;
import com.skillsphere.verification.domain.ProjectSkill;
import com.skillsphere.verification.domain.ProjectSkillRepository;
import com.skillsphere.verification.domain.Submission;
import com.skillsphere.verification.internal.SubmissionService;
import com.skillsphere.verification.internal.VivaService;
import com.skillsphere.skill.SkillLookup;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Projects, submissions, and the viva that defends one.
 *
 * <p>Submitting and starting the viva are two calls made back to back by this
 * controller, not one service method — {@link SubmissionService} and
 * {@link VivaService} stay independent so the slow one (Ollama inference)
 * never becomes a reason the fast one (saving a submission) has to change.
 */
@RestController
@RequestMapping("/api/verification")
@RequiredArgsConstructor
@Tag(name = "Verification", description = "Projects, submissions, the process ledger and the AI viva")
public class VerificationController {

    private final ProjectRepository projects;
    private final ProjectSkillRepository projectSkills;
    private final SubmissionService submissionService;
    private final VivaService vivaService;
    private final SkillLookup skillLookup;

    @GetMapping("/projects")
    @Operation(summary = "List published projects")
    public List<VerificationDtos.ProjectSummary> listProjects() {
        return projects.findAllPublished().stream().map(this::toSummary).toList();
    }

    @GetMapping("/projects/{projectId}")
    @Operation(summary = "One project's brief and rubric")
    public VerificationDtos.ProjectSummary project(@PathVariable Long projectId) {
        return toSummary(projects.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project", projectId)));
    }

    @PostMapping("/projects/{projectId}/draft")
    @Operation(summary = "Start or resume a draft submission for this project")
    public VerificationDtos.SubmissionView openDraft(@PathVariable Long projectId) {
        return toView(submissionService.openDraft(CurrentUser.requireId(), projectId));
    }

    @PostMapping("/submissions/{submissionId}/save")
    @Operation(summary = "Autosave the current draft content")
    public VerificationDtos.SubmissionView saveDraft(
            @PathVariable Long submissionId,
            @Valid @RequestBody VerificationDtos.SaveDraftRequest request) {
        return toView(submissionService.saveDraft(submissionId, CurrentUser.requireId(), request.content()));
    }

    @PostMapping("/submissions/{submissionId}/submit")
    @Operation(summary = "Submit and immediately start the viva",
            description = "The two happen together because a submission that requires a viva is not "
                    + "meaningfully 'done' until the defence starts — leaving it submitted-but-not-yet-"
                    + "defended would be a state with no honest label.")
    public VivaService.VivaResult submit(
            @PathVariable Long submissionId,
            @Valid @RequestBody VerificationDtos.SubmitRequest request) {
        Long userId = CurrentUser.requireId();
        Submission submission = submissionService.submit(submissionId, userId,
                request.toolUsed(), request.purpose(), request.extent(), request.detail());

        Project project = projects.findById(submission.getProjectId())
                .orElseThrow(() -> new NotFoundException("Project", submission.getProjectId()));
        if (!project.isRequiresViva()) {
            return new VivaService.VivaResult(null, "NOT_REQUIRED", null, null, 0, 0, null);
        }
        return vivaService.start(submissionId, userId);
    }

    @PostMapping("/viva/{vivaSessionId}/answer")
    @Operation(summary = "Answer the current question and receive the next one, or the verdict",
            description = "Scoring and the next question arrive in one call because the evaluation of "
                    + "this answer is what the follow-up question adapts to — they cannot be separate "
                    + "requests without the client holding state it has no business holding.")
    public VivaService.VivaResult answer(
            @PathVariable Long vivaSessionId,
            @Valid @RequestBody VerificationDtos.AnswerRequest request) {
        return vivaService.answer(vivaSessionId, CurrentUser.requireId(), request.turnId(), request.answer());
    }

    private VerificationDtos.ProjectSummary toSummary(Project p) {
        List<Long> skillIds = projectSkills.findByProjectId(p.getId()).stream()
                .map(ps -> ps.getId().getSkillId()).toList();
        var names = skillLookup.namesOf(skillIds);
        List<String> skillNames = skillIds.stream()
                .map(id -> names.getOrDefault(id, "Unknown skill"))
                .distinct().toList();
        return new VerificationDtos.ProjectSummary(p.getId(), p.getTitle(), p.getSlug(), p.getDescription(),
                p.getBrief(), p.getLevelBand().name(), p.getEstMinutes(), p.getRubric(), p.isRequiresViva(),
                skillNames);
    }

    private VerificationDtos.SubmissionView toView(Submission s) {
        return new VerificationDtos.SubmissionView(s.getId(), s.getProjectId(), s.getStatus().name(),
                s.getAttemptNo(), s.getContent(), s.getDraftCount());
    }
}
