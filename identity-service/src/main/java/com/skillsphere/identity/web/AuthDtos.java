package com.skillsphere.identity.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Request and response shapes for authentication.
 *
 * <p>Grouped in one file because they are a single cohesive contract read
 * together; splitting eight short records across eight files would spread one
 * idea thinly without making any of it clearer.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    /**
     * @param password minimum length is deliberately 12 rather than 8. Length
     *                 dominates every other password rule: a long passphrase
     *                 beats a short string with a symbol bolted on, and
     *                 composition rules mostly push people toward predictable
     *                 substitutions. No maximum below 72 either, since Argon2id
     *                 hashes the input regardless of length.
     * @param role     only LEARNER or INSTRUCTOR may be requested. ADMIN is
     *                 absent by construction — there is no self-service path to
     *                 an administrator account, and a request naming one is
     *                 rejected rather than silently downgraded.
     */
    public record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 12, max = 200,
                    message = "Password must be at least 12 characters") String password,
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank String role) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    /**
     * The refresh token is intentionally absent from this body. It is delivered
     * as an httpOnly cookie so JavaScript cannot read it, which means an XSS
     * flaw cannot exfiltrate the long-lived credential. Returning it here as
     * well would undo that protection entirely.
     */
    public record AuthResponse(
            String accessToken,
            String tokenType,
            Instant expiresAt,
            UserSummary user) {

        public static AuthResponse of(String accessToken, Instant expiresAt, UserSummary user) {
            return new AuthResponse(accessToken, "Bearer", expiresAt, user);
        }
    }

    public record UserSummary(
            Long id,
            String email,
            String fullName,
            String status,
            boolean emailVerified,
            List<String> roles) {
    }

    /** Returned by registration, which does not log the user in. */
    public record RegistrationResponse(
            Long userId,
            String status,
            String message) {
    }

    public record ForgotPasswordRequest(
            @NotBlank @Email String email) {
    }

    public record ResendVerificationRequest(
            @NotBlank @Email String email) {
    }

    /**
     * Minimum length matches registration. A reset that accepted a weaker
     * password than signup would be the easiest way to downgrade an account.
     */
    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 12, max = 200,
                    message = "Password must be at least 12 characters") String newPassword) {
    }

    public record MessageResponse(String message) {
    }

    // -------------------------------------------------------------------
    // MFA — TOTP (SKILLSPHERE.md §6, §12)
    // -------------------------------------------------------------------

    /**
     * Returned once, at enrollment. {@code secret} and {@code recoveryCodes}
     * never appear in any other response — only their encrypted/hashed forms
     * are ever persisted, so this is the caller's only chance to record them.
     *
     * @param secret         Base32, for an app that cannot scan a QR code
     * @param provisioningUri {@code otpauth://} URI — the frontend renders this as a QR code
     * @param recoveryCodes  ten single-use fallback codes
     */
    public record MfaEnrollResponse(
            String secret,
            String provisioningUri,
            List<String> recoveryCodes) {
    }

    /**
     * A login that requires a second factor gets this instead of an
     * {@link AuthResponse} — no tokens, and no refresh cookie is set. The
     * caller completes the session with {@code mfaToken} plus a code at
     * {@code POST /api/auth/mfa/verify}.
     */
    public record MfaChallengeResponse(
            boolean mfaRequired,
            String mfaToken,
            Instant expiresAt) {
    }

    /**
     * Serves both moments {@code /api/auth/mfa/verify} handles.
     *
     * <p>Confirming an enrollment (caller already authenticated): only
     * {@code code} is used. Completing a login (caller not authenticated
     * yet): {@code mfaToken} plus exactly one of {@code code} or
     * {@code recoveryCode}.
     */
    public record MfaVerifyRequest(
            String mfaToken,
            @Size(max = 20) String code,
            @Size(max = 20) String recoveryCode) {
    }

    /**
     * @param verified whether the code was accepted
     * @param session  present only when this call completed a login — absent
     *                 for an enrollment confirmation, which has no session to
     *                 issue since the caller was already signed in
     */
    public record MfaVerifyResponse(
            boolean verified,
            AuthResponse session) {
    }
}
