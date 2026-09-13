package com.skillsphere.identity.internal;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generates and hashes opaque security tokens — refresh tokens, email
 * verification links, password resets.
 *
 * <p><b>Generation</b> uses {@link SecureRandom} with 256 bits of entropy.
 * {@code Math.random}, {@code UUID.randomUUID} and timestamp-derived values are
 * all unsuitable: the first two are not cryptographically secure, and anything
 * derived from a clock is guessable by someone who knows roughly when an
 * account was created.
 *
 * <p><b>Hashing</b> uses plain SHA-256, deliberately unlike passwords. Argon2id
 * is slow on purpose because a password has maybe 40 bits of entropy and must
 * be made expensive to guess. These tokens carry 256 bits of true randomness,
 * so brute force is already impossible and a deliberately slow hash would only
 * add latency to every refresh — on the hot path, for no security gain.
 *
 * <p>Comparison is constant-time. A lookup by hash is usually indexed and
 * therefore not timing-sensitive, but direct comparisons happen too, and a
 * naive {@code equals} leaks how many leading characters matched.
 */
@Component
public class TokenGenerator {

    private static final int TOKEN_BYTES = 32; // 256 bits
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * @return a URL-safe token with no padding, so it can be dropped into a
     *         verification link without escaping
     */
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Hex-encoded SHA-256 of the token. Only this is ever stored. */
    public String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is mandated by the platform; absence means a broken JVM.
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    /** Constant-time comparison — never short-circuits on first difference. */
    public boolean matches(String token, String expectedHash) {
        return MessageDigest.isEqual(
                hash(token).getBytes(StandardCharsets.UTF_8),
                expectedHash.getBytes(StandardCharsets.UTF_8));
    }
}
