package com.skillsphere.shared.security;

import com.skillsphere.shared.error.ForbiddenException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Reads the authenticated caller from the security context.
 *
 * <p><b>Why this lives in the shared kernel rather than in identity.</b> Every
 * module needs to know who is calling — the skill engine, verification, career,
 * gamification. If the principal type belonged to identity, all of them would
 * have to depend on identity to ask that question, and the Modulith boundary
 * test would reject it. "Who is the caller" is genuinely cross-cutting, so it
 * belongs to the kernel; identity's job is to <em>authenticate</em>, not to own
 * the concept of a caller.
 *
 * <p>That distinction also survives the Sprint 6 split. Once identity is its own
 * service, other services still receive a principal from the gateway without
 * calling identity at all — which is only possible because they never depended
 * on its internals in the first place.
 *
 * <p>Only the id is ever used for authorisation. The email is display and
 * logging material: it is mutable, and treating a mutable field as an identity
 * is how accounts get confused with one another.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** The caller, or empty on an unauthenticated request. */
    public static Optional<UserPrincipal> get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return authentication.getPrincipal() instanceof UserPrincipal principal
                ? Optional.of(principal)
                : Optional.empty();
    }

    /**
     * The caller's id, or a 403 if there is none.
     *
     * <p>Throwing rather than returning null is deliberate: a service that
     * silently treats "nobody" as a user id would attribute one learner's
     * evidence to another, and that is a failure nothing downstream could
     * detect.
     */
    public static Long requireId() {
        return get().map(UserPrincipal::id)
                .orElseThrow(() -> new ForbiddenException("Authentication is required."));
    }

    public static boolean hasRole(String role) {
        return get().map(principal -> principal.hasRole(role)).orElse(false);
    }
}
