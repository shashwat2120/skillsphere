package com.skillsphere.identity.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * WebAuthn / passkey settings, bound from {@code skillsphere.security.webauthn}.
 *
 * @param rpId   the Relying Party id — must be the frontend's domain (or a
 *               registrable suffix of it), never the API's own origin if the
 *               two differ. A credential registered under one rpId will
 *               never verify under another, by design of the spec.
 * @param rpName human-readable name shown in the platform's passkey prompt
 * @param origin the exact origin (scheme + host + port) the browser reports
 *               performing the ceremony from — checked on every
 *               registration and authentication so a credential minted for
 *               this site cannot be replayed against a lookalike one
 */
@Validated
@ConfigurationProperties(prefix = "skillsphere.security.webauthn")
public record WebauthnProperties(
        @NotBlank String rpId,
        @NotBlank String rpName,
        @NotBlank String origin
) {
}
