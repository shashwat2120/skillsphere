package com.skillsphere.identity.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Request/response shapes for the passkey (WebAuthn) ceremonies.
 *
 * <p>The options responses are deliberately close to the shape the browser's
 * {@code navigator.credentials.create()} / {@code .get()} expect, so the
 * frontend does minimal reshaping before handing them to the WebAuthn API.
 * The register/authenticate requests carry the credential response back as
 * the raw JSON string produced by {@code PublicKeyCredential.toJSON()} —
 * webauthn4j parses that shape directly, so nothing here has to decompose it
 * field by field.
 */
public final class PasskeyDtos {

    private PasskeyDtos() {
    }

    public record PubKeyCredParam(String type, int alg) {
    }

    public record RegistrationOptionsResponse(
            String challenge,
            String rpId,
            String rpName,
            String userId,
            String userName,
            String userDisplayName,
            List<PubKeyCredParam> pubKeyCredParams,
            List<String> excludeCredentialIds,
            long timeoutMillis) {
    }

    public record RegisterPasskeyRequest(
            @NotBlank String credentialResponseJson,
            String deviceLabel) {
    }

    public record AuthenticationOptionsRequest(
            @NotBlank @Email String email) {
    }

    public record AuthenticationOptionsResponse(
            String challenge,
            String rpId,
            List<String> allowCredentialIds,
            long timeoutMillis) {
    }

    public record AuthenticatePasskeyRequest(
            @NotBlank @Email String email,
            @NotBlank String credentialId,
            @NotBlank String assertionResponseJson) {
    }
}
