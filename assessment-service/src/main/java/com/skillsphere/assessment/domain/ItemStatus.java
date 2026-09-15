package com.skillsphere.assessment.domain;

/** Lifecycle of an assessment item. */
public enum ItemStatus {
    DRAFT,
    ACTIVE,
    /**
     * Withdrawn from circulation but not deleted.
     *
     * <p>Responses reference it, and those responses are the evidence behind
     * skill claims already issued. Deleting a bad item would erase the working
     * behind a learner's passport rather than just stopping the item being asked
     * again.
     */
    RETIRED
}
