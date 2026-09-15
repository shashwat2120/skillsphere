package com.skillsphere.shared.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Best-effort client address, shared by everything that records one —
 * originally written once inside {@code AuthController} and hoisted here
 * when the admin audit trail needed the exact same logic a second time.
 *
 * <p>{@code X-Forwarded-For} is client-controlled and trivially spoofed, so
 * this is for diagnostics and audit trails only. Nothing security relevant —
 * rate limiting in particular — may key on it without a trusted proxy
 * configuration establishing which hops can be believed.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String from(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
