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

    public record MessageResponse(String message) {
    }
}
