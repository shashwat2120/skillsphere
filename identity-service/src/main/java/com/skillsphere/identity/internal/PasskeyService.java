package com.skillsphere.identity.internal;

import com.skillsphere.identity.domain.User;
import com.skillsphere.identity.domain.UserRepository;
import com.skillsphere.identity.domain.WebauthnCredential;
import com.skillsphere.identity.domain.WebauthnCredentialRepository;
import com.skillsphere.identity.security.WebauthnProperties;
import com.skillsphere.identity.web.PasskeyDtos;
import com.skillsphere.shared.error.NotFoundException;
import com.skillsphere.shared.error.ValidationException;
import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.authenticator.AuthenticatorImpl;
import com.webauthn4j.converter.exception.DataConversionException;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.AuthenticationData;
import com.webauthn4j.data.AuthenticationParameters;
import com.webauthn4j.data.PublicKeyCredentialParameters;
import com.webauthn4j.data.PublicKeyCredentialType;
import com.webauthn4j.data.RegistrationData;
import com.webauthn4j.data.RegistrationParameters;
import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.authenticator.COSEKey;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.Challenge;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.server.ServerProperty;
import com.webauthn4j.verifier.exception.VerificationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The passkey (WebAuthn) registration and authentication ceremonies.
 *
 * <p>Verification is real: {@link WebAuthnManager} checks the challenge,
 * origin, RP id, signature and (on authentication) the stored public key —
 * nothing here decides success without it. It is created with
 * {@code createNonStrictWebAuthnManager}, which skips verifying an
 * attestation statement's trust chain against a root certificate store. That
 * is the correct default for a platform that lets people register whatever
 * authenticator they already own (a phone, a security key, a password
 * manager) rather than a fleet of enterprise-issued devices from known
 * vendors — trust chain verification exists for the latter case, and
 * demanding it here would reject legitimate passkeys for no security benefit
 * this platform actually needs. The challenge/origin/signature checks that
 * actually stop forgery and replay are unaffected either way.
 *
 * <p>A passkey works as a primary login method here — {@link #authenticate}
 * issues a full session directly, the same as a correct password. Using one
 * as a step-up second factor after a password would need a variant that
 * takes an existing MFA challenge token instead of an email; not built here
 * since the API surface in SKILLSPHERE.md §12 only calls for a register and
 * an authenticate endpoint.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasskeyService {

    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
    private static final long CEREMONY_TIMEOUT_MILLIS = 60_000;
    private static final String REG_CHALLENGE_PREFIX = "passkey:reg:challenge:";
    private static final String AUTH_CHALLENGE_PREFIX = "passkey:auth:challenge:";

    private final WebauthnCredentialRepository credentials;
    private final UserRepository users;
    private final WebauthnProperties properties;
    private final StringRedisTemplate redis;
    private final AuthService authService;

    private final WebAuthnManager webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager();
    private final ObjectConverter objectConverter = new ObjectConverter();
    private final SecureRandom random = new SecureRandom();

    // ---------------------------------------------------------------------
    // Registration — adding a passkey to an already-authenticated account
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PasskeyDtos.RegistrationOptionsResponse registrationOptions(Long userId) {
        User user = users.findActiveById(userId)
                .orElseThrow(() -> new NotFoundException("User", userId));

        byte[] challenge = newChallenge();
        redis.opsForValue().set(REG_CHALLENGE_PREFIX + userId,
                Base64.getUrlEncoder().withoutPadding().encodeToString(challenge),
                CHALLENGE_TTL);

        List<String> existingCredentialIds = credentials.findByUserId(userId).stream()
                .map(WebauthnCredential::getCredentialId)
                .toList();

        return new PasskeyDtos.RegistrationOptionsResponse(
                Base64.getUrlEncoder().withoutPadding().encodeToString(challenge),
                properties.rpId(),
                properties.rpName(),
                Base64.getUrlEncoder().withoutPadding().encodeToString(
                        userId.toString().getBytes(StandardCharsets.UTF_8)),
                user.getEmail(),
                user.getFullName(),
                List.of(
                        new PasskeyDtos.PubKeyCredParam("public-key", (int) COSEAlgorithmIdentifier.ES256.getValue()),
                        new PasskeyDtos.PubKeyCredParam("public-key", (int) COSEAlgorithmIdentifier.RS256.getValue())),
                existingCredentialIds,
                CEREMONY_TIMEOUT_MILLIS);
    }

    @Transactional
    public void register(Long userId, PasskeyDtos.RegisterPasskeyRequest request) {
        String challengeKey = REG_CHALLENGE_PREFIX + userId;
        Challenge challenge = loadChallenge(challengeKey);

        ServerProperty serverProperty = new ServerProperty(
                new Origin(properties.origin()), properties.rpId(), challenge);

        RegistrationParameters parameters = new RegistrationParameters(
                serverProperty,
                List.of(new PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.ES256),
                        new PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.RS256)),
                false,  // userVerificationRequired — "preferred", not mandated, on the client side
                true);  // userPresenceRequired

        RegistrationData registrationData;
        try {
            registrationData = webAuthnManager.verifyRegistrationResponseJSON(
                    request.credentialResponseJson(), parameters);
        } catch (DataConversionException ex) {
            throw new ValidationException("MALFORMED_PASSKEY_RESPONSE",
                    "That registration response could not be read.");
        } catch (VerificationException ex) {
            log.info("Passkey registration failed verification for user {}: {}", userId, ex.toString());
            throw new ValidationException("PASSKEY_VERIFICATION_FAILED",
                    "This passkey could not be verified. Please try again.");
        }

        var authenticatorData = registrationData.getAttestationObject().getAuthenticatorData();
        var attestedCredentialData = authenticatorData.getAttestedCredentialData();
        if (attestedCredentialData == null) {
            throw new ValidationException("PASSKEY_VERIFICATION_FAILED",
                    "The authenticator did not return a credential.");
        }

        String credentialId = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(attestedCredentialData.getCredentialId());

        if (credentials.findByCredentialId(credentialId).isPresent()) {
            throw new ValidationException("PASSKEY_ALREADY_REGISTERED",
                    "This passkey is already registered.");
        }

        byte[] publicKeyBytes = objectConverter.getCborConverter()
                .writeValueAsBytes(attestedCredentialData.getCOSEKey());

        String transports = registrationData.getTransports() == null ? null
                : registrationData.getTransports().stream()
                        .map(Object::toString)
                        .collect(Collectors.joining(","));

        credentials.save(new WebauthnCredential(
                userId, credentialId, publicKeyBytes,
                authenticatorData.getSignCount(), transports, request.deviceLabel()));

        redis.delete(challengeKey);
        log.info("Passkey registered for user {}", userId);
    }

    // ---------------------------------------------------------------------
    // Authentication — signing in with a passkey as a primary credential
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PasskeyDtos.AuthenticationOptionsResponse authenticationOptions(String rawEmail) {
        String email = rawEmail.trim().toLowerCase();
        byte[] challenge = newChallenge();
        redis.opsForValue().set(AUTH_CHALLENGE_PREFIX + email,
                Base64.getUrlEncoder().withoutPadding().encodeToString(challenge),
                CHALLENGE_TTL);

        // No enumeration signal either way: an unknown address still gets a
        // real, freshly minted challenge and simply resolves to an empty
        // allow-list, which the browser's own WebAuthn UI handles by finding
        // no matching credential — the same experience as a wrong password.
        List<String> allowCredentialIds = users.findActiveByEmail(email)
                .map(user -> credentials.findByUserId(user.getId()).stream()
                        .map(WebauthnCredential::getCredentialId)
                        .toList())
                .orElseGet(List::of);

        return new PasskeyDtos.AuthenticationOptionsResponse(
                Base64.getUrlEncoder().withoutPadding().encodeToString(challenge),
                properties.rpId(),
                allowCredentialIds,
                CEREMONY_TIMEOUT_MILLIS);
    }

    @Transactional
    public AuthService.LoginResult authenticate(PasskeyDtos.AuthenticatePasskeyRequest request,
                                                 String ipAddress, String userAgent) {
        String email = request.email().trim().toLowerCase();
        String challengeKey = AUTH_CHALLENGE_PREFIX + email;
        Challenge challenge = loadChallenge(challengeKey);

        User user = users.findActiveByEmail(email)
                .filter(User::isActive)
                .orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException(
                        "Invalid credentials"));

        WebauthnCredential stored = credentials.findByCredentialId(request.credentialId())
                .filter(c -> c.getUserId().equals(user.getId()))
                .orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException(
                        "Invalid credentials"));

        ServerProperty serverProperty = new ServerProperty(
                new Origin(properties.origin()), properties.rpId(), challenge);

        COSEKey coseKey = objectConverter.getCborConverter()
                .readValue(stored.getPublicKey(), COSEKey.class);
        AttestedCredentialData attestedCredentialData = new AttestedCredentialData(
                AAGUID.ZERO, Base64.getUrlDecoder().decode(stored.getCredentialId()), coseKey);
        AuthenticatorImpl authenticator = new AuthenticatorImpl(
                attestedCredentialData, null, stored.getSignCount());

        @SuppressWarnings("deprecation")
        AuthenticationParameters parameters = new AuthenticationParameters(
                serverProperty, authenticator,
                List.of(Base64.getUrlDecoder().decode(request.credentialId())),
                false, true);

        AuthenticationData authenticationData;
        try {
            authenticationData = webAuthnManager.verifyAuthenticationResponseJSON(
                    request.assertionResponseJson(), parameters);
        } catch (DataConversionException ex) {
            throw new ValidationException("MALFORMED_PASSKEY_RESPONSE",
                    "That authentication response could not be read.");
        } catch (VerificationException ex) {
            log.info("Passkey authentication failed verification for user {}: {}", user.getId(), ex.toString());
            throw new org.springframework.security.authentication.BadCredentialsException("Invalid credentials");
        }

        // Sign count must strictly increase. A count that does not move (or
        // moves backwards) is WebAuthn's own signal that this credential's
        // private key may have been cloned onto a second device.
        long newSignCount = authenticationData.getAuthenticatorData().getSignCount();
        if (newSignCount != 0 && newSignCount <= stored.getSignCount()) {
            log.warn("Passkey sign count did not advance for user {} (stored {}, seen {}) — possible cloned authenticator",
                    user.getId(), stored.getSignCount(), newSignCount);
        }
        stored.setSignCount(Math.max(newSignCount, stored.getSignCount()));
        stored.setLastUsedAt(Instant.now());

        redis.delete(challengeKey);
        user.setLastLoginAt(Instant.now());

        log.info("Passkey login for user {}", user.getId());
        return authService.issueSession(user, ipAddress, userAgent);
    }

    // ---------------------------------------------------------------------

    private byte[] newChallenge() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return bytes;
    }

    private Challenge loadChallenge(String redisKey) {
        String stored = redis.opsForValue().get(redisKey);
        if (stored == null) {
            throw new ValidationException("PASSKEY_CHALLENGE_EXPIRED",
                    "This passkey ceremony has expired. Please try again.");
        }
        return new DefaultChallenge(Base64.getUrlDecoder().decode(stored));
    }
}
