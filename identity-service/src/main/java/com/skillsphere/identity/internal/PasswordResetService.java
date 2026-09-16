package com.skillsphere.identity.internal;

import com.skillsphere.identity.domain.PasswordResetToken;
import com.skillsphere.identity.domain.PasswordResetTokenRepository;
import com.skillsphere.identity.domain.RefreshToken;
import com.skillsphere.identity.domain.User;
import com.skillsphere.identity.domain.UserRepository;
import com.skillsphere.identity.events.PasswordChanged;
import com.skillsphere.identity.events.PasswordResetRequested;
import com.skillsphere.shared.error.ValidationException;
import com.skillsphere.shared.ratelimit.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * Password reset.
 *
 * <p>Three decisions carry this flow, and each exists because the obvious
 * implementation is subtly dangerous.
 *
 * <p><b>The response never reveals whether an account exists.</b> Requesting a
 * reset always reports success, whatever address is submitted. The natural
 * version — "no account with that email" — turns this endpoint into a free
 * account-enumeration oracle, and it is the most commonly shipped leak of its
 * kind precisely because the unhelpful version feels unhelpful.
 *
 * <p><b>Changing a password destroys every session.</b> Not tidiness: a reset is
 * most often performed by someone whose account has already been compromised.
 * Leaving existing sessions alive means the attacker keeps their access and the
 * victim believes they have fixed the problem, which is worse than not
 * resetting at all.
 *
 * <p><b>A confirmation is sent afterwards, unrequested.</b> If the user did not
 * make the change, that message is the only signal they get that their account
 * was taken, and the only opportunity to act while it still matters.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    /**
     * Deliberately shorter than email verification's twenty-four hours. This
     * token replaces a credential rather than confirming an address, so a link
     * left sitting in an inbox is a standing key to the account.
     */
    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    private static final int MAX_REQUESTS = 3;
    private static final Duration REQUEST_WINDOW = Duration.ofMinutes(15);
    private static final int MIN_PASSWORD_LENGTH = 12;

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final TokenGenerator tokenGenerator;
    private final SessionRevoker sessionRevoker;
    private final RateLimiter rateLimiter;
    private final ApplicationEventPublisher events;
    private final HaveIBeenPwnedService haveIBeenPwned;

    /**
     * Starts a reset. Always succeeds from the caller's point of view.
     *
     * <p>Rate limited per address so this cannot be used to flood someone's
     * inbox — an endpoint that sends mail to an arbitrary address on demand is
     * otherwise a small spam cannon pointed at whoever the attacker dislikes.
     */
    @Transactional
    public void requestReset(String rawEmail) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);

        if (!rateLimiter.check("pwreset:" + email, MAX_REQUESTS, REQUEST_WINDOW).allowed()) {
            // Silently ignored rather than reported. Telling the caller they hit
            // a limit confirms the address is worth limiting.
            log.info("Password reset throttled for an address");
            return;
        }

        Optional<User> found = users.findActiveByEmail(email);
        if (found.isEmpty()) {
            log.info("Password reset requested for an unknown address — reporting success anyway");
            return;
        }

        User user = found.get();
        if (!user.isActive()) {
            // A suspended account must not be recoverable by its owner; that is
            // the entire point of the suspension.
            log.info("Password reset ignored for {} account {}", user.getStatus(), user.getId());
            return;
        }

        // Retire earlier requests so only the newest link works. Otherwise three
        // requests leave three live keys scattered across an inbox.
        tokens.invalidateOutstanding(user.getId(), Instant.now());

        String plaintext = tokenGenerator.generate();
        Instant expiresAt = Instant.now().plus(TOKEN_TTL);

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(tokenGenerator.hash(plaintext));
        token.setExpiresAt(expiresAt);
        token.setCreatedAt(Instant.now());
        tokens.save(token);

        events.publishEvent(new PasswordResetRequested(
                user.getId(), user.getEmail(), user.getFullName(), plaintext, expiresAt));

        log.info("Password reset issued for user {}", user.getId());
    }

    /**
     * Completes a reset.
     *
     * <p>Unlike the request step, failure here is reported plainly. The caller
     * already holds a token, so telling them it has expired reveals nothing they
     * could not determine by trying it — and leaving them to guess why a valid-
     * looking link does nothing is a support burden with no security benefit.
     */
    @Transactional
    public void resetPassword(String presentedToken, String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new ValidationException("PASSWORD_TOO_SHORT",
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (haveIBeenPwned.isPwned(newPassword)) {
            throw new ValidationException("PASSWORD_BREACHED",
                    "This password has appeared in a known data breach — choose another.");
        }

        PasswordResetToken token = tokens.findByTokenHash(tokenGenerator.hash(presentedToken))
                .orElseThrow(() -> new ValidationException("INVALID_TOKEN",
                        "This reset link is not valid."));

        if (!token.isUsable()) {
            throw new ValidationException("INVALID_TOKEN",
                    "This reset link has expired or has already been used.");
        }

        User user = token.getUser();
        token.consume();
        user.setPasswordHash(passwordEncoder.encode(newPassword));

        // The decisive step. A reset usually follows a compromise, so every
        // existing session is destroyed — otherwise the attacker keeps their
        // access while the victim believes the problem is solved.
        sessionRevoker.revokeAllSessions(user.getId(), RefreshToken.RevocationReason.PASSWORD_CHANGED);

        events.publishEvent(new PasswordChanged(user.getId(), user.getEmail(), user.getFullName()));

        log.info("Password reset completed for user {} — all sessions revoked", user.getId());
    }
}
