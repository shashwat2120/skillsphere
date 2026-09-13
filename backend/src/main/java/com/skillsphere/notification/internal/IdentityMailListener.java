package com.skillsphere.notification.internal;

import com.skillsphere.identity.events.EmailVerificationRequested;
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
}
