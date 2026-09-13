package com.skillsphere.notification.internal;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Sender identity and link construction for outgoing mail.
 *
 * <p>{@code appBaseUrl} is configuration rather than something derived from the
 * incoming request. Building a verification link from a request header is a
 * genuine vulnerability: an attacker sets {@code Host} or
 * {@code X-Forwarded-Host} to a domain they control, triggers a password reset
 * for a victim, and the victim receives a legitimate-looking email whose link
 * delivers a valid token straight to the attacker.
 */
@Validated
@ConfigurationProperties(prefix = "skillsphere.mail")
public record MailProperties(
        @NotBlank String fromAddress,
        @NotBlank String fromName,
        @NotBlank String appBaseUrl) {
}
