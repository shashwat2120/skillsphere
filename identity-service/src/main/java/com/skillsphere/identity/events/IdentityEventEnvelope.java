package com.skillsphere.identity.events;

import java.time.Instant;

/**
 * The wire shape identity's events take once they leave this process.
 *
 * <p>Sprint 5's in-process events ({@link EmailVerificationRequested} and
 * the rest, still published and still travelling through Modulith's
 * transactional outbox exactly as before) are a Java type any module in the
 * same JVM could depend on. Once identity is its own process, "any module
 * in the same JVM" is nobody — the notification module lives in a different
 * process now — so those records stop being the actual contract and become
 * an internal implementation detail that {@link KafkaEventBridge} translates
 * from.
 *
 * <p>One flat, nullable-everything record rather than five typed messages:
 * with no shared library between this service and its consumers (adding one
 * would reintroduce exactly the deploy-together coupling extraction is
 * supposed to remove), the consumer has to define its own copy of whatever
 * shape this is regardless, and a single envelope with an {@code eventType}
 * discriminator is the smaller, more stable thing to keep in sync by hand
 * than five separate message classes would be.
 */
public record IdentityEventEnvelope(
        String eventType,
        Long userId,
        String email,
        String fullName,
        String token,
        Instant expiresAt,
        String reason) {

    public static final String TOPIC = "identity-events";

    public static IdentityEventEnvelope from(EmailVerificationRequested e) {
        return new IdentityEventEnvelope("EMAIL_VERIFICATION_REQUESTED",
                e.userId(), e.email(), e.fullName(), e.verificationToken(), e.expiresAt(), null);
    }

    public static IdentityEventEnvelope from(PasswordResetRequested e) {
        return new IdentityEventEnvelope("PASSWORD_RESET_REQUESTED",
                e.userId(), e.email(), e.fullName(), e.resetToken(), e.expiresAt(), null);
    }

    public static IdentityEventEnvelope from(PasswordChanged e) {
        return new IdentityEventEnvelope("PASSWORD_CHANGED",
                e.userId(), e.email(), e.fullName(), null, null, null);
    }

    public static IdentityEventEnvelope from(InstructorApproved e) {
        return new IdentityEventEnvelope("INSTRUCTOR_APPROVED",
                e.userId(), e.email(), e.fullName(), null, null, null);
    }

    public static IdentityEventEnvelope from(AccountSuspended e) {
        return new IdentityEventEnvelope("ACCOUNT_SUSPENDED",
                e.userId(), e.email(), null, null, null, e.reason());
    }
}
