package com.skillsphere.identity.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * JWT settings, bound from {@code skillsphere.security.jwt}.
 *
 * <p>Validated at startup rather than trusted at runtime. A secret that is
 * absent or too short is a catastrophic misconfiguration — HMAC-SHA256 with a
 * short key is brute-forceable, and an attacker who recovers it can mint tokens
 * for any account including an admin. Failing to boot is the correct response;
 * the alternative is an application that runs happily while being trivially
 * forgeable.
 *
 * @param secret             HMAC signing key, minimum 32 bytes for HS256
 * @param accessTokenTtl     lifetime of an access token
 * @param refreshTokenTtl    lifetime of a refresh token
 * @param issuer             {@code iss} claim, checked on validation
 */
@Validated
@ConfigurationProperties(prefix = "skillsphere.security.jwt")
public record JwtProperties(

        @NotBlank
        @Size(min = 32, message = "JWT secret must be at least 32 bytes for HS256")
        String secret,

        @Min(1)
        Duration accessTokenTtl,

        @Min(1)
        Duration refreshTokenTtl,

        @NotBlank
        String issuer
) {
    public JwtProperties {
        // Short access tokens are the mitigation for JWT's central weakness:
        // a stateless token stays valid until it expires, so the window in
        // which a stolen one is useful is exactly its lifetime. Fifteen minutes
        // keeps that window small while the refresh token carries the session.
        if (accessTokenTtl == null) {
            accessTokenTtl = Duration.ofMinutes(15);
        }
        if (refreshTokenTtl == null) {
            refreshTokenTtl = Duration.ofDays(30);
        }
    }
}
