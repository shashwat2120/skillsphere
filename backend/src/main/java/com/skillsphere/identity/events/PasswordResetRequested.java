package com.skillsphere.identity.events;

import java.time.Instant;

/**
 * Published when someone asks to reset a password.
 *
 * <p>The plaintext token travels in the event because it exists exactly once —
 * only its hash is persisted, so nothing downstream could recover it afterwards.
 */
public record PasswordResetRequested(
        Long userId,
        String email,
        String fullName,
        String resetToken,
        Instant expiresAt) {
}
