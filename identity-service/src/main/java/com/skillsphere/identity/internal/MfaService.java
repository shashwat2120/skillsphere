package com.skillsphere.identity.internal;

import com.skillsphere.identity.domain.MfaRecoveryCode;
import com.skillsphere.identity.domain.MfaRecoveryCodeRepository;
import com.skillsphere.identity.domain.MfaTotp;
import com.skillsphere.identity.domain.MfaTotpRepository;
import com.skillsphere.identity.domain.User;
import com.skillsphere.identity.domain.UserRepository;
import com.skillsphere.identity.security.JwtService;
import com.skillsphere.identity.web.AuthDtos;
import com.skillsphere.shared.error.ConflictException;
import com.skillsphere.shared.error.NotFoundException;
import com.skillsphere.shared.error.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * TOTP enrollment and the second-factor challenge at login, per
 * SKILLSPHERE.md §6.
 *
 * <p>{@code /api/auth/mfa/verify} is deliberately one endpoint serving two
 * different moments, matching the API surface in §12: confirming a freshly
 * generated secret during enrollment ({@link #confirmEnrollment}, called by
 * an already-authenticated user) and clearing the second-factor challenge
 * that {@link AuthService#login} raises for an account with MFA enabled
 * ({@link #verifyLogin}, called by someone who is not authenticated yet —
 * that is the entire point of the challenge token). {@code AuthController}
 * decides which one applies based on whether the caller is authenticated.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MfaService {

    private static final int RECOVERY_CODE_COUNT = 10;
    private static final int RECOVERY_CODE_BYTES = 4; // -> 8 hex chars, shown as XXXX-XXXX

    private final MfaTotpRepository mfaTotps;
    private final MfaRecoveryCodeRepository recoveryCodes;
    private final UserRepository users;
    private final TotpService totp;
    private final MfaSecretCipher cipher;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthService authService;

    private final SecureRandom random = new SecureRandom();

    /**
     * Starts (or restarts) enrollment: generates a fresh secret and a fresh
     * batch of recovery codes, and returns the plaintext of both. This is the
     * only moment either value exists outside this method — the secret is
     * encrypted before it is stored, and the codes are hashed.
     */
    @Transactional
    public AuthDtos.MfaEnrollResponse enroll(Long userId) {
        User user = users.findActiveById(userId)
                .orElseThrow(() -> new NotFoundException("User", userId));

        mfaTotps.findById(userId).ifPresent(existing -> {
            if (existing.isEnabled()) {
                throw new ConflictException("MFA_ALREADY_ENABLED",
                        "TOTP is already enabled on this account.");
            }
        });

        // Any earlier, never-confirmed attempt is retired rather than left
        // alongside the new one — only one secret and one batch of codes
        // should ever be live for an account at a time.
        mfaTotps.deleteByUserId(userId);
        recoveryCodes.deleteByUserId(userId);

        String secret = totp.generateSecret();
        mfaTotps.save(new MfaTotp(userId, cipher.encrypt(secret)));

        List<String> plaintextCodes = generateRecoveryCodes();
        recoveryCodes.saveAll(plaintextCodes.stream()
                .map(code -> new MfaRecoveryCode(userId, passwordEncoder.encode(code)))
                .toList());

        log.info("TOTP enrollment started for user {}", userId);
        return new AuthDtos.MfaEnrollResponse(
                secret, totp.provisioningUri(secret, user.getEmail()), plaintextCodes);
    }

    /**
     * Confirms enrollment by checking a code against the secret issued by
     * {@link #enroll}. Until this succeeds, {@code enabled} stays false and
     * login is unaffected — a half-finished enrollment must never lock
     * someone out of an account they cannot yet prove they can still access.
     */
    @Transactional
    public void confirmEnrollment(Long userId, String code) {
        MfaTotp entity = mfaTotps.findById(userId)
                .orElseThrow(() -> new ValidationException("MFA_NOT_ENROLLED",
                        "Start enrollment with /api/auth/mfa/totp/enroll first."));

        if (entity.isEnabled()) {
            throw new ConflictException("MFA_ALREADY_ENABLED",
                    "TOTP is already enabled on this account.");
        }

        String secret = cipher.decrypt(entity.getSecretEncrypted());
        if (!totp.verifyCode(secret, code)) {
            throw new ValidationException("INVALID_MFA_CODE", "That code is not valid.");
        }

        entity.confirm();
        log.info("TOTP enrollment confirmed for user {}", userId);
    }

    /**
     * Finishes a login that {@link AuthService#login} paused for a second
     * factor. Accepts either a live TOTP code or a single-use recovery code —
     * exactly one should be supplied, and the caller is responsible for that
     * (see {@code AuthDtos.MfaVerifyRequest}).
     *
     * <p>Every failure path — expired challenge, unknown user, wrong code —
     * collapses to the same {@link BadCredentialsException} the primary login
     * endpoint throws, for the same enumeration reasons documented on
     * {@link AuthService}.
     */
    @Transactional
    public AuthService.LoginResult verifyLogin(String mfaToken, String code, String recoveryCode,
                                                String ipAddress, String userAgent) {
        Long userId = jwtService.parseMfaChallenge(mfaToken)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired MFA challenge"));

        User user = users.findActiveById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired MFA challenge"));

        MfaTotp entity = mfaTotps.findById(userId)
                .filter(MfaTotp::isEnabled)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired MFA challenge"));

        boolean verified;
        if (code != null && !code.isBlank()) {
            verified = totp.verifyCode(cipher.decrypt(entity.getSecretEncrypted()), code);
        } else if (recoveryCode != null && !recoveryCode.isBlank()) {
            verified = consumeRecoveryCode(userId, recoveryCode);
        } else {
            throw new ValidationException("MFA_CODE_REQUIRED", "A code or recovery code is required.");
        }

        if (!verified) {
            throw new BadCredentialsException("Invalid credentials");
        }

        user.setLastLoginAt(Instant.now());
        log.info("MFA verified for user {} — session issued", userId);
        return authService.issueSession(user, ipAddress, userAgent);
    }

    private boolean consumeRecoveryCode(Long userId, String candidate) {
        List<MfaRecoveryCode> usable = recoveryCodes.findByUserIdAndUsedAtIsNull(userId);
        for (MfaRecoveryCode entity : usable) {
            if (passwordEncoder.matches(candidate, entity.getCodeHash())) {
                entity.consume();
                log.warn("Recovery code consumed for user {} — {} remain", userId, usable.size() - 1);
                return true;
            }
        }
        return false;
    }

    private List<String> generateRecoveryCodes() {
        List<String> codes = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            codes.add(randomRecoveryCode());
        }
        return codes;
    }

    private String randomRecoveryCode() {
        byte[] bytes = new byte[RECOVERY_CODE_BYTES];
        random.nextBytes(bytes);
        String hex = HexFormat.of().formatHex(bytes).toUpperCase(Locale.ROOT);
        return hex.substring(0, 4) + "-" + hex.substring(4, 8);
    }
}
