package com.skillsphere.verification.internal;

import com.skillsphere.shared.error.ForbiddenException;
import com.skillsphere.shared.error.NotFoundException;
import com.skillsphere.shared.error.ValidationException;
import com.skillsphere.verification.domain.AiUsageDeclaration;
import com.skillsphere.verification.domain.AiUsageDeclarationRepository;
import com.skillsphere.verification.domain.AiUsageExtent;
import com.skillsphere.verification.domain.AiUsagePurpose;
import com.skillsphere.verification.domain.Project;
import com.skillsphere.verification.domain.ProjectRepository;
import com.skillsphere.verification.domain.ProcessEvent;
import com.skillsphere.verification.domain.ProcessEventRepository;
import com.skillsphere.verification.domain.ProcessEventType;
import com.skillsphere.verification.domain.Submission;
import com.skillsphere.verification.domain.SubmissionRepository;
import com.skillsphere.verification.domain.SubmissionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The project workspace: start a draft, save it, submit it.
 *
 * <p>What happens after submission — the viva — is deliberately a separate
 * service ({@link VivaService}). Ollama calls are slow and this module is
 * isolated specifically so that CPU-heavy inference never sits on the same
 * request path as a learner saving a paragraph of draft text.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final ProjectRepository projects;
    private final SubmissionRepository submissions;
    private final ProcessEventRepository processEvents;
    private final AiUsageDeclarationRepository aiDeclarations;

    public Submission openDraft(Long userId, Long projectId) {
        Project project = requireProject(projectId);

        return submissions.findFirstByUserIdAndProjectIdAndStatusOrderByAttemptNoDesc(
                        userId, projectId, SubmissionStatus.DRAFT)
                .orElseGet(() -> {
                    int nextAttempt = submissions.findByUserIdAndProjectIdOrderByAttemptNoDesc(userId, projectId)
                            .stream().findFirst().map(s -> s.getAttemptNo() + 1).orElse(1);
                    Submission draft = submissions.save(new Submission(userId, project.getId(), nextAttempt));
                    processEvents.save(new ProcessEvent(draft.getId(), ProcessEventType.SESSION_START, "{}"));
                    return draft;
                });
    }

    @Transactional
    public Submission saveDraft(Long submissionId, Long userId, String content) {
        Submission submission = requireOwnedDraft(submissionId, userId);
        boolean largePaste = content != null && submission.getContent() != null
                && content.length() - submission.getContent().length() > 500;

        submission.setContent(content);
        submission.recordDraft();
        if (largePaste) {
            submission.recordLargePaste();
        }
        submissions.save(submission);

        processEvents.save(new ProcessEvent(submissionId,
                largePaste ? ProcessEventType.LARGE_PASTE : ProcessEventType.DRAFT_SAVED, "{}"));

        return submission;
    }

    @Transactional
    public Submission submit(Long submissionId, Long userId, String toolUsed, AiUsagePurpose purpose,
                              AiUsageExtent extent, String detail) {
        Submission submission = requireOwnedDraft(submissionId, userId);
        if (submission.getContent() == null || submission.getContent().isBlank()) {
            throw new ValidationException("EMPTY_SUBMISSION", "Nothing has been written yet.");
        }

        submission.submit();
        submissions.save(submission);

        aiDeclarations.save(new AiUsageDeclaration(submissionId, toolUsed, purpose, extent, detail));
        processEvents.save(new ProcessEvent(submissionId, ProcessEventType.SESSION_END, "{}"));

        log.info("Submission {} submitted by user {} (attempt {}, {} drafts, {} large pastes)",
                submissionId, userId, submission.getAttemptNo(),
                submission.getDraftCount(), submission.getLargePasteCount());

        return submission;
    }

    public Submission requireOwnedDraft(Long submissionId, Long userId) {
        Submission submission = submissions.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("Submission", submissionId));
        if (!submission.getUserId().equals(userId)) {
            throw new ForbiddenException("That submission belongs to another learner.");
        }
        if (submission.getStatus() != SubmissionStatus.DRAFT) {
            throw new ValidationException("NOT_A_DRAFT", "This submission has already been submitted.");
        }
        return submission;
    }

    private Project requireProject(Long projectId) {
        return projects.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project", projectId));
    }
}
