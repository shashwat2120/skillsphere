package com.skillsphere.verification.domain;

import com.skillsphere.shared.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One attempt at a project.
 *
 * <p>Holds the artefact itself ({@code content}, or links to one) and the
 * process signals derived from the ledger once the attempt is sealed. Those
 * signals describe the <em>shape</em> of the work — how many drafts, how
 * large a single paste was — never a verdict about whether AI produced it.
 * The verdict comes only from the viva.
 */
@Entity
@Table(name = "submissions")
@Getter
@Setter
@NoArgsConstructor
public class Submission extends AuditableEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubmissionStatus status = SubmissionStatus.DRAFT;

    @Column(columnDefinition = "text")
    private String content;

    @Column(name = "repo_url", length = 500)
    private String repoUrl;

    @Column(name = "artefact_url", length = 500)
    private String artefactUrl;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo = 1;

    @Column(name = "active_minutes")
    private Integer activeMinutes;

    @Column(name = "draft_count", nullable = false)
    private int draftCount = 0;

    @Column(name = "large_paste_count", nullable = false)
    private int largePasteCount = 0;

    @Column(name = "first_activity_at")
    private Instant firstActivityAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    public Submission(Long userId, Long projectId, int attemptNo) {
        this.userId = userId;
        this.projectId = projectId;
        this.attemptNo = attemptNo;
        this.firstActivityAt = Instant.now();
    }

    public void recordDraft() {
        draftCount++;
    }

    public void recordLargePaste() {
        largePasteCount++;
    }

    public void submit() {
        this.status = SubmissionStatus.SUBMITTED;
        this.submittedAt = Instant.now();
        if (firstActivityAt != null) {
            // Wall-clock elapsed, not idle-subtracted active time — the
            // column name promises more precision than this generator gives
            // it. Deriving genuine active time needs walking the process
            // ledger for idle gaps, which is real future work rather than a
            // number worth faking here.
            this.activeMinutes = (int) java.time.Duration.between(firstActivityAt, submittedAt).toMinutes();
        }
    }
}
