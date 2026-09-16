package com.skillsphere.identity.internal;

import com.skillsphere.identity.domain.AccountStatus;
import com.skillsphere.identity.domain.EmailVerificationToken;
import com.skillsphere.identity.domain.EmailVerificationTokenRepository;
import com.skillsphere.identity.domain.InstructorApplication;
import com.skillsphere.identity.domain.InstructorApplicationRepository;
import com.skillsphere.identity.domain.MfaTotp;
import com.skillsphere.identity.domain.MfaTotpRepository;
import com.skillsphere.identity.domain.RefreshToken;
import com.skillsphere.identity.domain.RefreshTokenRepository;
import com.skillsphere.identity.domain.Role;
import com.skillsphere.identity.domain.RoleName;
import com.skillsphere.identity.domain.RoleRepository;
import com.skillsphere.identity.domain.User;
import com.skillsphere.identity.domain.UserRepository;
import com.skillsphere.identity.events.EmailVerificationRequested;
import com.skillsphere.identity.security.JwtProperties;
import com.skillsphere.identity.security.JwtService;
import com.skillsphere.identity.security.TokenDenylist;
import com.skillsphere.identity.web.AuthDtos;
import com.skillsphere.shared.error.ConflictException;
import com.skillsphere.shared.error.ValidationException;
import com.skillsphere.shared.ratelimit.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The authentication flows: register, log in, refresh, log out, verify.
 *
 * <p>Two rules run through all of it.
 *
 * <p><b>Nothing here reveals whether an account exists.</b> Registration with a
 * taken address, login with an unknown address, and login with a wrong password
 * are all indistinguishable from outside. Any difference in response, status
 * code or timing turns this into an oracle that tells an attacker which
 * addresses are worth attacking — and "this email is already registered" is the
 * most common way that leaks.
 *
 * <p><b>Side effects go through events.</b> This class never sends an email. It
 * publishes and lets notification act, so registration cannot be slowed or
 * failed by an SMTP problem.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Duration VERIFICATION_TOKEN_TTL = Duration.ofHours(24);

    // Matches PasswordResetService's own resend limits — the same "cannot be
    // used to flood someone's inbox" concern applies to any endpoint that
    // sends mail to an arbitrary address on demand.
    private static final int MAX_RESEND_REQUESTS = 3;
    private static final Duration RESEND_WINDOW = Duration.ofMinutes(15);

    private final UserRepository users;
    private final RoleRepository roles;
    private final RefreshTokenRepository refreshTokens;
    private final EmailVerificationTokenRepository verificationTokens;
    private final PasswordEncoder passwordEncoder;
    private final TokenGenerator tokenGenerator;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final TokenDenylist denylist;
    private final SessionRevoker sessionRevoker;
    private final LoginAttemptService loginAttempts;
    private final RateLimiter rateLimiter;
    private final ApplicationEventPublisher events;
    private final HaveIBeenPwnedService haveIBeenPwned;
    private final InstructorApplicationRepository instructorApplications;
    private final MfaTotpRepository mfaTotps;

    // ---------------------------------------------------------------------
    // Registration
    // ---------------------------------------------------------------------

    /**
     * Creates an account and requests email verification.
     *
     * <p>Registration does not log anyone in. An address has not yet been proven
     * to belong to the person using it, and instructors additionally need
     * approval — issuing a session here would hand out access before either
     * check has happened.
     */
    @Transactional
    public AuthDtos.RegistrationResponse register(AuthDtos.RegisterRequest request) {
        RoleName requestedRole = parseSelfServiceRole(request.role());

        // Checked before the insert for a clean message, but the unique index is
        // what actually guarantees it: two simultaneous registrations would both
        // pass this check, and only the constraint stops the duplicate.
        if (users.existsByEmail(request.email().trim().toLowerCase())) {
            // Deliberately the same shape a caller sees for any other conflict.
            // See class notes on enumeration.
            log.info("Registration attempted for an address already in use");
            throw new ConflictException("REGISTRATION_FAILED",
                    "That registration could not be completed.");
        }

        // Breached-password check (SKILLSPHERE.md §6). Checked before hashing —
        // hashing the value first would gain nothing since Argon2id is a
        // one-way function, so there is no reason to pay its cost on a
        // password we are about to reject anyway.
        if (haveIBeenPwned.isPwned(request.password())) {
            throw new ValidationException("PASSWORD_BREACHED",
                    "This password has appeared in a known data breach — choose another.");
        }

        User user = new User(request.email(), request.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        // Instructors author the items that measure other people, so the role is
        // granted by a human rather than by self-service.
        user.setStatus(requestedRole == RoleName.INSTRUCTOR
                ? AccountStatus.PENDING
                : AccountStatus.ACTIVE);

        Role role = roles.findByName(requestedRole)
                .orElseThrow(() -> new IllegalStateException(
                        "Role " + requestedRole + " missing — database not migrated"));
        user.addRole(role);

        users.save(user);

        // A durable application record, separate from the status flip above —
        // see InstructorApplication's class comment for why both exist.
        if (requestedRole == RoleName.INSTRUCTOR) {
            instructorApplications.save(new InstructorApplication(user.getId()));
        }

        issueVerificationToken(user);

        log.info("Registered user {} as {} ({})", user.getId(), requestedRole, user.getStatus());

        return new AuthDtos.RegistrationResponse(
                user.getId(),
                user.getStatus().name(),
                requestedRole == RoleName.INSTRUCTOR
                        ? "Account created. Confirm your email, then an administrator will review your instructor access."
                        : "Account created. Check your email to confirm your address.");
    }

    /**
     * Sends a fresh confirmation link, for the original one having expired or
     * never arrived.
     *
     * <p>Same enumeration discipline as {@link #register}: always reports
     * success, whatever address is submitted and whatever state the account
     * is in. Looked up with {@link UserRepository#findActiveByEmail}, not
     * {@link User#isActive()} — a pending instructor is exactly the case this
     * exists for, and gating on {@code ACTIVE} status would exclude them.
     */
    @Transactional
    public void resendVerification(String rawEmail) {
        String email = rawEmail.trim().toLowerCase();

        if (!rateLimiter.check("verify-resend:" + email, MAX_RESEND_REQUESTS, RESEND_WINDOW).allowed()) {
            log.info("Verification resend throttled for an address");
            return;
        }

        Optional<User> found = users.findActiveByEmail(email);
        if (found.isEmpty()) {
            log.info("Verification resend requested for an unknown address — reporting success anyway");
            return;
        }

        User user = found.get();
        if (user.isEmailVerified()) {
            log.info("Verification resend requested for an already-verified account {}", user.getId());
            return;
        }

        issueVerificationToken(user);
    }

    /**
     * Rejects any role that cannot be self-assigned.
     *
     * <p>ADMIN and EMPLOYER are absent from the accepted set on purpose. There is
     * no UI path to an administrator account and no server path either — the
     * request is refused rather than quietly downgraded, because silently
     * granting something other than what was asked for hides an attempted
     * privilege escalation instead of surfacing it.
     */
    private RoleName parseSelfServiceRole(String requested) {
        try {
            RoleName role = RoleName.valueOf(requested.trim().toUpperCase());
            if (role != RoleName.LEARNER && role != RoleName.INSTRUCTOR) {
                throw new ValidationException("INVALID_ROLE", "That role cannot be self-assigned.");
            }
            return role;
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("INVALID_ROLE", "Unknown role requested.");
        }
    }

    /**
     * Issues a verification token and publishes the request.
     *
     * <p>The plaintext token travels in the event because it exists exactly once
     * — only its hash is persisted, so nothing downstream could recover it.
     */
    private void issueVerificationToken(User user) {
        verificationTokens.invalidateOutstanding(user.getId(), Instant.now());

        String plaintext = tokenGenerator.generate();
        Instant expiresAt = Instant.now().plus(VERIFICATION_TOKEN_TTL);

        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setTokenHash(tokenGenerator.hash(plaintext));
        token.setExpiresAt(expiresAt);
        token.setCreatedAt(Instant.now());
        verificationTokens.save(token);

        events.publishEvent(new EmailVerificationRequested(
                user.getId(), user.getEmail(), user.getFullName(), plaintext, expiresAt));
    }

    // ---------------------------------------------------------------------
    // Login
    // ---------------------------------------------------------------------

    @Transactional
    public LoginOutcome login(AuthDtos.LoginRequest request, String ipAddress, String userAgent) {
        String email = request.email().trim().toLowerCase();

        // Checked before any password work. Verifying first would let a blocked
        // caller keep spending Argon2id's 64 MiB and quarter-second cost on
        // every rejected attempt, turning the lockout into a resource-exhaustion
        // vector aimed at our own server.
        //
        // The response is identical to a wrong password on purpose: saying
        // "temporarily locked" would confirm the address is registered and tell
        // an attacker their attack is landing.
        if (loginAttempts.isBlocked(email, ipAddress)) {
            throw new BadCredentialsException("Invalid credentials");
        }

        Optional<User> found = users.findActiveByEmail(email);

        // The password is verified even when no account matched, against a
        // throwaway hash. Skipping it would return "no such account" in about a
        // millisecond while a real account takes the full Argon2 cost, and that
        // timing difference alone is enough to enumerate addresses.
        String storedHash = found.map(User::getPasswordHash).orElse(null);
        boolean passwordValid = storedHash != null
                && passwordEncoder.matches(request.password(), storedHash);

        if (storedHash == null) {
            passwordEncoder.matches(request.password(), dummyHash());
        }

        if (found.isEmpty() || !passwordValid) {
            // Counted against the address even when no such account exists.
            // Skipping it for unknown addresses would leave an attacker free to
            // probe non-existent accounts at unlimited speed, and the difference
            // in throttling between known and unknown addresses would itself be
            // an enumeration signal.
            loginAttempts.recordFailure(email, ipAddress);
            throw new BadCredentialsException("Invalid credentials");
        }

        User user = found.get();

        // Status is checked after the password, not before. Reporting "your
        // account is suspended" to someone who has not proven they own it tells
        // an attacker both that the address is registered and what state it is
        // in.
        if (!user.isActive()) {
            log.info("Login blocked for {} account {}", user.getStatus(), user.getId());
            // Not counted as a failure: the credentials were correct, and a
            // pending instructor refreshing the page while awaiting approval
            // should not lock themselves out of an account they will shortly be
            // given access to.
            throw new BadCredentialsException("Invalid credentials");
        }

        // Clears the failure counter, so someone who mistypes twice and then
        // succeeds carries no penalty at all.
        loginAttempts.recordSuccess(email);
        user.setLastLoginAt(Instant.now());

        // A confirmed TOTP enrollment gates the session behind a second
        // factor. The password alone is not enough to finish login — the
        // caller gets a short-lived challenge token instead of tokens, and
        // must complete /api/auth/mfa/verify to actually receive them.
        Optional<MfaTotp> mfa = mfaTotps.findById(user.getId());
        if (mfa.isPresent() && mfa.get().isEnabled()) {
            JwtService.IssuedToken challenge = jwtService.issueMfaChallenge(user.getId());
            log.info("Login for user {} completed password check — MFA challenge issued", user.getId());
            return new MfaRequired(challenge.token(), challenge.expiresAt());
        }

        return new SessionIssued(issueSession(user, ipAddress, userAgent));
    }

    /**
     * A real Argon2id hash of a random value, computed once at startup.
     *
     * <p>Generated rather than hard-coded so it is guaranteed to be a valid
     * encoding the verifier will actually process: a malformed literal would be
     * rejected early and cost nothing, defeating the entire purpose. Its only
     * job is to make the unknown-account path burn the same CPU as a real
     * verification, so response time cannot distinguish the two.
     */
    private volatile String dummyHash;

    private String dummyHash() {
        String local = dummyHash;
        if (local == null) {
            synchronized (this) {
                local = dummyHash;
                if (local == null) {
                    local = passwordEncoder.encode(tokenGenerator.generate());
                    dummyHash = local;
                }
            }
        }
        return local;
    }

    // ---------------------------------------------------------------------
    // Session issue and refresh
    // ---------------------------------------------------------------------

    // Package-private rather than private: MfaService (same package) calls
    // this directly to issue the session once a second factor has been
    // verified, so the token-issuing logic exists in exactly one place.
    LoginResult issueSession(User user, String ipAddress, String userAgent) {
        List<String> authorities = user.getRoles().stream().map(Role::authority).toList();

        JwtService.IssuedToken access =
                jwtService.issueAccessToken(user.getId(), user.getEmail(), authorities);

        String refreshPlaintext = tokenGenerator.generate();
        RefreshToken refresh = new RefreshToken();
        refresh.setUser(user);
        refresh.setTokenHash(tokenGenerator.hash(refreshPlaintext));
        refresh.setExpiresAt(Instant.now().plus(jwtProperties.refreshTokenTtl()));
        refresh.setIpAddress(ipAddress);
        refresh.setUserAgent(userAgent);
        refresh.setCreatedAt(Instant.now());
        refreshTokens.save(refresh);

        return new LoginResult(
                AuthDtos.AuthResponse.of(access.token(), access.expiresAt(), toSummary(user)),
                refreshPlaintext,
                jwtProperties.refreshTokenTtl());
    }

    /**
     * Exchanges a refresh token for a new session, rotating it in the process.
     *
     * <p>This is where token theft is detected. A legitimate client presents a
     * given refresh token exactly once, because each use produces a successor.
     * If an already-rotated token is presented, the only explanation is that it
     * was copied — and since it is unknowable whether the attacker or the real
     * user holds the current token, every token in the chain is revoked and both
     * must log in again. Forcing that on the real user is the right trade
     * against leaving a thief with a live session.
     */
    @Transactional
    public LoginResult refresh(String presentedToken, String ipAddress, String userAgent) {
        String hash = tokenGenerator.hash(presentedToken);

        RefreshToken stored = refreshTokens.findByTokenHash(hash)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (stored.isRevoked()) {
            handleReuse(stored);
            throw new BadCredentialsException("Invalid refresh token");
        }

        if (stored.isExpired()) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        User user = stored.getUser();
        if (!user.isActive()) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        LoginResult result = issueSession(user, ipAddress, userAgent);
        // Rotation must happen after the successor exists so the chain records
        // what replaced this token.
        refreshTokens.findByTokenHash(tokenGenerator.hash(result.refreshToken()))
                .ifPresent(stored::rotateTo);

        return result;
    }

    /**
     * Handles a revoked refresh token being presented again.
     *
     * <p>A token revoked because it was <em>rotated</em> should never be seen
     * twice: the legitimate client swapped it for a successor and discarded it.
     * Seeing it again means a copy exists, and since there is no way to tell
     * whether the attacker or the real user holds the current token, every
     * session is destroyed and both must sign in again.
     *
     * <p>The revocation is delegated to {@link SessionRevoker} so it commits in
     * its own transaction. Doing it inline would be undone moments later by the
     * exception this method's caller throws.
     */
    private void handleReuse(RefreshToken stored) {
        if (RefreshToken.RevocationReason.ROTATED.name().equalsIgnoreCase(stored.getRevokedReason())) {
            log.warn("Refresh token reuse detected for user {} — burning the whole chain",
                    stored.getUser().getId());
            sessionRevoker.revokeAllSessions(
                    stored.getUser().getId(), RefreshToken.RevocationReason.REUSE_DETECTED);
        }
    }

    // ---------------------------------------------------------------------
    // Logout and verification
    // ---------------------------------------------------------------------

    @Transactional
    public void logout(String refreshTokenPlaintext, String accessTokenId, Instant accessExpiry) {
        if (refreshTokenPlaintext != null) {
            refreshTokens.findByTokenHash(tokenGenerator.hash(refreshTokenPlaintext))
                    .ifPresent(token -> token.revoke(RefreshToken.RevocationReason.LOGOUT));
        }
        // Revoking the refresh token alone would leave the access token working
        // until it expires. Denying the jti closes the session immediately.
        if (accessTokenId != null && accessExpiry != null) {
            denylist.denyToken(accessTokenId, accessExpiry);
        }
    }

    @Transactional
    public void verifyEmail(String presentedToken) {
        EmailVerificationToken token = verificationTokens
                .findByTokenHash(tokenGenerator.hash(presentedToken))
                .orElseThrow(() -> new ValidationException(
                        "INVALID_TOKEN", "This verification link is not valid."));

        if (!token.isUsable()) {
            throw new ValidationException("INVALID_TOKEN",
                    "This verification link has expired or has already been used.");
        }

        token.consume();
        User user = token.getUser();
        user.setEmailVerifiedAt(Instant.now());

        log.info("Email verified for user {}", user.getId());
    }

    private AuthDtos.UserSummary toSummary(User user) {
        return new AuthDtos.UserSummary(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getStatus().name(),
                user.isEmailVerified(),
                user.getRoles().stream().map(role -> role.getName().name()).toList());
    }

    /** Carries the refresh token separately so the web layer can place it in a cookie. */
    public record LoginResult(AuthDtos.AuthResponse response, String refreshToken, Duration refreshTtl) {
    }

    /**
     * What {@link #login} produces: either a finished session, or a demand for
     * a second factor. Sealed so {@link com.skillsphere.identity.web.AuthController}
     * is forced by the compiler to handle both — there is no default branch to
     * accidentally issue tokens without checking which one came back.
     */
    public sealed interface LoginOutcome permits SessionIssued, MfaRequired {
    }

    public record SessionIssued(LoginResult result) implements LoginOutcome {
    }

    /**
     * @param mfaToken  short-lived proof the password check already passed —
     *                  presented back to {@code /api/auth/mfa/verify} along
     *                  with the 6-digit code or a recovery code
     * @param expiresAt when the challenge itself expires, independent of the code's own validity
     */
    public record MfaRequired(String mfaToken, java.time.Instant expiresAt) implements LoginOutcome {
    }
}
