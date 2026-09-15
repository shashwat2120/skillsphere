package com.skillsphere.identity.internal;

import com.skillsphere.shared.ratelimit.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Brute-force and credential-stuffing defence for the login endpoint.
 *
 * <p><b>Two counters, because there are two different attacks.</b>
 *
 * <p>Limiting only by IP address fails against credential stuffing, where an
 * attacker holds a leaked list of addresses and passwords and tries each
 * combination once, spread across a botnet. No single IP looks busy and no
 * single account sees many attempts from one place, yet thousands of accounts
 * are being probed.
 *
 * <p>Limiting only by account fails the other way: an attacker can lock a victim
 * out of their own account on demand simply by submitting wrong passwords, which
 * turns a security control into a denial-of-service weapon aimed at real users.
 *
 * <p>So both are counted, with deliberately different consequences. The IP limit
 * is strict and short — it throttles machinery. The account limit is looser and
 * temporary, and it never permanently disables the account: a lockout that an
 * anonymous attacker can trigger must always expire on its own.
 *
 * <p><b>Counting failures, not requests.</b> A successful login clears the
 * account counter, so a legitimate person who mistypes twice and then succeeds
 * is not penalised at all.
 *
 * <p><b>The response never changes.</b> A locked-out attempt returns the same
 * 401 as a wrong password. Telling a caller "this account is temporarily locked"
 * confirms the address is registered and reveals that the attack is working,
 * which is precisely the feedback an attacker wants.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {


    private final RateLimiter rateLimiter;
    private final LoginRateLimitProperties limits;

    /**
     * Checked before any password verification happens.
     *
     * <p>Ordering matters: verifying first would let an attacker keep spending
     * Argon2id's 64 MiB and quarter-second cost on every blocked request, which
     * turns the login endpoint into a resource-exhaustion target.
     *
     * @return true when this attempt should be refused without being processed
     */
    public boolean isBlocked(String email, String ipAddress) {
        if (ipAddress != null && !rateLimiter.check(ipKey(ipAddress), limits.ipAttempts(), limits.ipWindow()).allowed()) {
            log.warn("Login throttled for address {} — too many attempts", ipAddress);
            return true;
        }
        // Read, never incremented. This counter tracks *failures*, and whether
        // this attempt is a failure cannot be known until the password has been
        // checked. Incrementing here would mean every attempt — including
        // correct ones — pushed the account toward lockout, so an attacker could
        // lock a victim out using nothing but the victim's own valid password.
        return !rateLimiter.peek(accountKey(email), limits.accountFailures()).allowed();
    }

    /** Records a failed attempt, locking the account once the threshold is crossed. */
    public void recordFailure(String email, String ipAddress) {
        RateLimiter.Decision decision =
                rateLimiter.check(accountKey(email), limits.accountFailures(), limits.accountWindow());

        if (!decision.allowed()) {
            log.warn("Account {} temporarily locked after repeated failures from {}",
                    maskEmail(email), ipAddress);
        }
    }

    /**
     * Clears the account's failure counter after a successful sign-in.
     *
     * <p>The IP counter is deliberately left alone. It limits request volume
     * rather than failures, and clearing it on success would let an attacker who
     * holds one valid credential reset the throttle at will and keep hammering
     * other accounts from the same address.
     */
    public void recordSuccess(String email) {
        rateLimiter.reset(accountKey(email));
    }

    private String ipKey(String ipAddress) {
        return "login:ip:" + ipAddress;
    }

    private String accountKey(String email) {
        return "login:account:" + email.trim().toLowerCase(Locale.ROOT);
    }

    /** Addresses are personal data; logs are widely readable. */
    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
