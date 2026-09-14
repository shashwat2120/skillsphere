package com.skillsphere.notification.internal;

import com.skillsphere.identity.events.EmailVerificationRequested;
import com.skillsphere.identity.events.InstructorApproved;
import com.skillsphere.identity.events.PasswordChanged;
import com.skillsphere.identity.events.PasswordResetRequested;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
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
 * independently.
 *
 * <p>{@link ApplicationModuleListener} is the important annotation. It combines
 * {@code @Async}, {@code @Transactional(propagation = REQUIRES_NEW)} and
 * {@code @TransactionalEventListener(AFTER_COMMIT)}, which together give the
 * three properties this needs:
 *
 * <ul>
 *   <li>the email is sent only after the account is actually committed, so a
 *       rolled-back registration never produces a verification link to an
 *       account that does not exist;</li>
 *   <li>it runs off the request thread, so an SMTP server taking four seconds
 *       does not make registration take four seconds;</li>
 *   <li>the event is recorded in the outbox first, so if this process dies
 *       mid-send the publication stays incomplete and is retried on restart
 *       rather than being lost.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityMailListener {

    private final MailService mailService;
    private final MailProperties properties;

    @ApplicationModuleListener
    void on(EmailVerificationRequested event) {
        log.debug("Preparing verification email for user {}", event.userId());

        String link = properties.appBaseUrl()
                + "/verify-email?token="
                + URLEncoder.encode(event.verificationToken(), StandardCharsets.UTF_8);

        mailService.send(
                event.email(),
                "Confirm your SkillSphere account",
                "verify-email",
                Map.of(
                        "fullName", event.fullName(),
                        "verificationLink", link,
                        "expiresAt", event.expiresAt()));
    }

    @ApplicationModuleListener
    void on(PasswordResetRequested event) {
        log.debug("Preparing password reset email for user {}", event.userId());

        String link = properties.appBaseUrl()
                + "/reset-password?token="
                + URLEncoder.encode(event.resetToken(), StandardCharsets.UTF_8);

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
    @ApplicationModuleListener
    void on(PasswordChanged event) {
        mailService.send(
                event.email(),
                "Your SkillSphere password was changed",
                "password-changed",
                Map.of("fullName", event.fullName()));
    }

    @ApplicationModuleListener
    void on(InstructorApproved event) {
        mailService.send(
                event.email(),
                "Your instructor access has been approved",
                "instructor-approved",
                Map.of("fullName", event.fullName()));
    }
}
