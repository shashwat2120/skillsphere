package com.skillsphere.identity.internal;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * RFC 6238 TOTP — generation and verification, hand-rolled rather than pulled
 * from a library.
 *
 * <p>There is genuinely nothing here a dependency would save: RFC 6238 is
 * HOTP (RFC 4226) applied to a 30-second time counter instead of an
 * incrementing one, HOTP is one {@code HmacSHA1} call over an 8-byte counter,
 * and the JDK ships {@link Mac} directly. The only non-trivial piece is Base32
 * (RFC 4648 §6), needed because every authenticator app expects the shared
 * secret typed or QR-scanned in Base32, and the JDK has no built-in encoder
 * for it — implemented below rather than adding a dependency for eight lines
 * of bit-shifting.
 */
@Component
public class TotpService {

    private static final int SECRET_BYTES = 20;        // 160 bits — RFC 4226 §4 recommendation
    private static final int DIGITS = 6;
    private static final int PERIOD_SECONDS = 30;
    private static final int ALLOWED_DRIFT_STEPS = 1;   // ±1 step tolerates modest clock skew
    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final String ISSUER = "SkillSphere";
    private static final char[] BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    private final SecureRandom random = new SecureRandom();

    /** A fresh 160-bit shared secret, Base32-encoded for display/QR and for storage. */
    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return base32Encode(bytes);
    }

    /**
     * The {@code otpauth://} URI an authenticator app scans as a QR code.
     * Returned as plain text — rendering it into a QR image is a frontend
     * concern, not this service's.
     */
    public String provisioningUri(String secretBase32, String accountEmail) {
        String label = urlEncode(ISSUER) + ":" + urlEncode(accountEmail);
        return "otpauth://totp/" + label
                + "?secret=" + secretBase32
                + "&issuer=" + urlEncode(ISSUER)
                + "&algorithm=SHA1"
                + "&digits=" + DIGITS
                + "&period=" + PERIOD_SECONDS;
    }

    /**
     * Checks a 6-digit code against the secret, allowing one step of clock
     * drift either side of now.
     */
    public boolean verifyCode(String secretBase32, String code) {
        if (code == null || !code.matches("\\d{" + DIGITS + "}")) {
            return false;
        }
        long currentStep = java.time.Instant.now().getEpochSecond() / PERIOD_SECONDS;
        boolean matched = false;
        // Every candidate is computed and compared, rather than returning on
        // the first hit, so verification takes the same time whichever step
        // (or no step) matches.
        for (long step = currentStep - ALLOWED_DRIFT_STEPS; step <= currentStep + ALLOWED_DRIFT_STEPS; step++) {
            String candidate = generateCode(secretBase32, step);
            if (constantTimeEquals(candidate, code)) {
                matched = true;
            }
        }
        return matched;
    }

    private String generateCode(String secretBase32, long counter) {
        try {
            byte[] key = base32Decode(secretBase32);
            byte[] counterBytes = ByteBuffer.allocate(8).putLong(counter).array();

            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(counterBytes);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int otp = binary % (int) Math.pow(10, DIGITS);
            return String.format(Locale.ROOT, "%0" + DIGITS + "d", otp);
        } catch (GeneralSecurityException ex) {
            // HmacSHA1 is mandated by every JVM; absence means a broken runtime.
            throw new IllegalStateException("HmacSHA1 unavailable", ex);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(
                a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    // -------------------------------------------------------------------
    // Base32 (RFC 4648 §6) — no padding on encode, padding tolerated on decode
    // -------------------------------------------------------------------

    private static String base32Encode(byte[] data) {
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1F;
                out.append(BASE32_ALPHABET[index]);
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            out.append(BASE32_ALPHABET[index]);
        }
        return out.toString();
    }

    private static byte[] base32Decode(String encoded) {
        String cleaned = encoded.trim().toUpperCase(Locale.ROOT).replace("=", "");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (char c : cleaned.toCharArray()) {
            int value = indexOf(c);
            if (value < 0) {
                continue; // ignore stray formatting characters (spaces, hyphens)
            }
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }

    private static int indexOf(char c) {
        for (int i = 0; i < BASE32_ALPHABET.length; i++) {
            if (BASE32_ALPHABET[i] == c) {
                return i;
            }
        }
        return -1;
    }
}
