package com.skillsphere.identity.events;

/**
 * Published when an account is suspended.
 *
 * <p>Carries the reason so a consumer can tell the person what happened. Silent
 * suspension is a support burden: the user only discovers it by being unable to
 * sign in, and has no idea why.
 */
public record AccountSuspended(Long userId, String email, String reason) {
}
