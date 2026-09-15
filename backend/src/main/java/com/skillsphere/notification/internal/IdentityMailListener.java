package com.skillsphere.notification.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Turns identity events into email.
 *
 * <p>This class is the only thing that knows registration should produce a
 * message. Identity states that an address needs verifying; what happens next is
 * entirely this module's concern, so the two can be changed — or deployed —
 * independently, which now includes running as entirely separate processes.
 *
 * <p><b>Kafka, not {@code @ApplicationModuleListener}.</b> Identity used to
 * live in this same JVM, and Modulith's transactional-outbox listener gave
 * this exactly the guarantees a mail send needs: only after the triggering
 * change actually commits, off the request thread, replayed on restart if
 * this process dies mid-send. Now that identity is its own process
 * ({@code identity-service}), an in-process event has nobody to deliver to
 * across that boundary — see identity-service's own {@code KafkaEventBridge}
 * for the other half of this change. A Kafka consumer group keeps two of the
 * three guarantees for free (durable, replayed from the last committed
 * offset on restart) and the third — never firing for a rolled-back write —
 * is identity-service's responsibility now, not this listener's: it only
 * ever receives an event that already made it onto the topic, which
 * KafkaEventBridge only does from inside identity's own outbox-backed
 * listener, after commit.
 *
 * <p>Four of the five event types identity-service publishes are handled
 * below, unchanged from before this class moved to Kafka.
 * {@code ACCOUNT_SUSPENDED} was never wired to an email in Sprint 5 either —
 * that gap is real, not something this migration should silently invent a
 * fix for, so the switch below simply has no case for it, exactly as this
 * class previously had no listener method for it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityMailListener {

    private final MailService mailService;
    private final MailProperties properties;

    @KafkaListener(topics = "identity-events", groupId = "skillsphere-backend-notification")
    void on(IdentityEventEnvelope event) {
        switch (event.eventType()) {
            case "EMAIL_VERIFICATION_REQUESTED" -> onEmailVerificationRequested(event);
            case "PASSWORD_RESET_REQUESTED" -> onPasswordResetRequested(event);
            case "PASSWORD_CHANGED" -> onPasswordChanged(event);
            case "INSTRUCTOR_APPROVED" -> onInstructorApproved(event);
            default -> log.debug("No mail handling for identity event type {}", event.eventType());
        }
    }

    private void onEmailVerificationRequested(IdentityEventEnvelope event) {
        log.debug("Preparing verification email for user {}", event.userId());

        String link = properties.appBaseUrl()
                + "/verify-email?token="
                + URLEncoder.encode(event.token(), StandardCharsets.UTF_8);

        mailService.send(
                event.email(),
                "Confirm your SkillSphere account",
                "verify-email",
                Map.of(
                        "fullName", event.fullName(),
                        "verificationLink", link,
                        "expiresAt", event.expiresAt()));
    }

    private void onPasswordResetRequested(IdentityEventEnvelope event) {
        log.debug("Preparing password reset email for user {}", event.userId());

        String link = properties.appBaseUrl()
                + "/reset-password?token="
                + URLEncoder.encode(event.token(), StandardCharsets.UTF_8);

        mailService.send(
                event.email(),
                "Reset your SkillSphere password",
                "reset-password",
                Map.of(
                        "fullName", event.fullName(),
                        "resetLink", link,
                        "expiresAt", event.expiresAt()));
    }

    /**
     * Confirms a password change that nobody asked to be told about.
     *
     * <p>Deliberately unrequested. If the recipient did not make the change, this
     * is the only signal they get that their account has been taken, and the only
     * chance to act while it still matters. Skipping it would make a successful
     * takeover completely silent.
     */
    private void onPasswordChanged(IdentityEventEnvelope event) {
        mailService.send(
                event.email(),
                "Your SkillSphere password was changed",
                "password-changed",
                Map.of("fullName", event.fullName()));
    }

    private void onInstructorApproved(IdentityEventEnvelope event) {
        mailService.send(
                event.email(),
                "Your instructor access has been approved",
                "instructor-approved",
                Map.of("fullName", event.fullName()));
    }
}
