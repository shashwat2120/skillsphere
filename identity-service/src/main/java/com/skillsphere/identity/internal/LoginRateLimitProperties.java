package com.skillsphere.identity.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Tunable thresholds for login throttling and account lockout.
 *
 * <p>Configuration rather than constants for two reasons. Operationally, the
 * right numbers depend on real traffic — too strict and a university computer
 * lab behind one NAT address locks out a whole class, too loose and the control
 * does nothing — and that should be adjustable without a redeploy. Practically,
 * hard-coded limits make the behaviour untestable: every test runs from the same
 * loopback address, so a fixed per-IP cap would have tests throttling each other
 * and failing depending on the order they happened to run in.
 *
 * @param ipAttempts        requests allowed per address per window, successful or not
 * @param ipWindow          how long the per-address window lasts
 * @param accountFailures   consecutive failures before an account is locked
 * @param accountWindow     how long a lockout lasts before it clears itself
 */
@ConfigurationProperties(prefix = "skillsphere.security.login-limits")
public record LoginRateLimitProperties(
        Integer ipAttempts,
        Duration ipWindow,
        Integer accountFailures,
        Duration accountWindow) {

    public LoginRateLimitProperties {
        // Strict: an address making more than this in a minute is machinery,
        // not a person typing.
        if (ipAttempts == null) {
            ipAttempts = 10;
        }
        if (ipWindow == null) {
            ipWindow = Duration.ofMinutes(1);
        }
        // Looser and self-expiring. Generous enough that a forgetful person is
        // unaffected, tight enough that guessing is impractical — and temporary,
        // because a lockout an anonymous attacker can trigger must never be
        // permanent or it becomes a denial-of-service tool aimed at real users.
        if (accountFailures == null) {
            accountFailures = 8;
        }
        if (accountWindow == null) {
            accountWindow = Duration.ofMinutes(15);
        }
    }
}
