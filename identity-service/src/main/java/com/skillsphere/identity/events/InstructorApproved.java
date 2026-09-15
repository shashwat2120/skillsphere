package com.skillsphere.identity.events;

/**
 * Published when an administrator grants instructor access.
 *
 * <p>Part of identity's public event API. Notification listens and sends the
 * confirmation; identity does not know or care that an email results, which is
 * what lets the two be deployed separately in Sprint 6.
 */
public record InstructorApproved(Long userId, String email, String fullName) {
}
