package com.skillsphere.verification.domain;

/**
 * Where a submission stands.
 *
 * <p>{@code VERIFIED} and {@code NOT_VERIFIED} are both terminal, and
 * deliberately not "accepted" and "rejected" — the work itself is not being
 * judged as good or bad, only whether the learner demonstrated understanding
 * of it under questioning. A rejected submission implies the work was
 * inadequate; a not-verified one says only that the defence did not succeed,
 * and the learner may resubmit.
 */
public enum SubmissionStatus {
    DRAFT,
    SUBMITTED,
    IN_VIVA,
    AWAITING_REVIEW,
    VERIFIED,
    NOT_VERIFIED
}
