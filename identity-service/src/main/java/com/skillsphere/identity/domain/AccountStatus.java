package com.skillsphere.identity.domain;

/**
 * Lifecycle state of an account, kept separate from its roles.
 *
 * <p>Role answers "what may this person do"; status answers "may this person do
 * anything at all". Conflating them is a common mistake — it leaves no way to
 * suspend an instructor without destroying the ownership records attached to
 * their courses.
 */
public enum AccountStatus {

    /** Normal operation. */
    ACTIVE,

    /**
     * Registered but awaiting admin approval. Instructors start here: authoring
     * rights carry real weight, since an instructor writes the items that
     * measure other people, so the role is granted by a human rather than by
     * self-service.
     */
    PENDING,

    /**
     * Blocked by an admin. Takes effect on the next request, not at the next
     * login, so an existing session cannot outlive the suspension.
     */
    SUSPENDED
}
