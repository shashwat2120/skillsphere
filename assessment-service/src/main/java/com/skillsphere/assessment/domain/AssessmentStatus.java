package com.skillsphere.assessment.domain;

public enum AssessmentStatus {
    IN_PROGRESS,
    COMPLETED,
    /**
     * Left unfinished.
     *
     * <p>Kept distinct from COMPLETED because the responses given before
     * abandonment are still valid evidence and still count toward mastery — the
     * learner answered them honestly. What is not valid is treating the resulting
     * ability estimate as final.
     */
    ABANDONED
}
