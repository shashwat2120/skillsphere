package com.skillsphere.verification.internal;

import com.skillsphere.verification.EvidenceLookup;
import com.skillsphere.verification.domain.Evidence;
import com.skillsphere.verification.domain.EvidenceRepository;
import com.skillsphere.verification.domain.EvidenceSourceType;
import com.skillsphere.verification.domain.Project;
import com.skillsphere.verification.domain.ProjectRepository;
import com.skillsphere.verification.domain.Submission;
import com.skillsphere.verification.domain.SubmissionRepository;
import com.skillsphere.verification.domain.VivaSession;
import com.skillsphere.verification.domain.VivaSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Implements the verification module's public evidence API.
 *
 * <p>Resolving {@code sourceTitle} means walking viva session → submission →
 * project for every row. Doing that per-row rather than batching is a
 * deliberate simplification: a single learner's evidence table has, at most,
 * a handful of rows for the lifetime of this project — the N+1 that would
 * matter at scale does not matter yet, and batching it now would be
 * complexity spent on a problem this product doesn't have.
 */
@Service
@RequiredArgsConstructor
public class EvidenceLookupService implements EvidenceLookup {

    private final EvidenceRepository evidence;
    private final VivaSessionRepository vivaSessions;
    private final SubmissionRepository submissions;
    private final ProjectRepository projects;

    @Override
    @Transactional(readOnly = true)
    public List<EvidenceItem> findByUserId(Long userId) {
        return evidence.findByUserIdOrderByVerifiedAtDesc(userId).stream()
                .map(this::toItem)
                .toList();
    }

    private EvidenceItem toItem(Evidence e) {
        String title = resolveSourceTitle(e);
        return new EvidenceItem(e.getId(), e.getSkillId(), e.getEvidenceType().name(),
                e.getSourceType().name(), e.getSourceId(), title,
                e.getWeight().doubleValue(), e.getScore() == null ? 0.0 : e.getScore().doubleValue(),
                e.getVerifiedAt());
    }

    private String resolveSourceTitle(Evidence e) {
        if (e.getSourceType() != EvidenceSourceType.VIVA_SESSION) {
            return e.getSourceType().name();
        }
        return vivaSessions.findById(e.getSourceId())
                .map(VivaSession::getSubmissionId)
                .flatMap(submissions::findById)
                .map(Submission::getProjectId)
                .flatMap(projects::findById)
                .map(Project::getTitle)
                .orElse("Project");
    }
}
