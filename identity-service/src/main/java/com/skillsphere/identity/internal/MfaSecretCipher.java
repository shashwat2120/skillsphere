package com.skillsphere.identity.internal;

import com.skillsphere.identity.security.JwtProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Encrypts TOTP secrets at rest, per {@code mfa_totp.secret_encrypted}'s
 * column comment.
 *
 * <p><b>Key material.</b> Rather than introduce a second application secret
 * to provision and rotate, the AES key is derived by SHA-256 of the existing
 * JWT signing secret ({@link JwtProperties#secret()}) — already a 32+ byte
 * value held only in server configuration, already validated at startup, and
 * already the credential whose compromise would let an attacker forge tokens
 * outright. It is not reused directly: SHA-256 keeps the AES key
 * cryptographically independent of the HMAC key derived from the same bytes.
 *
 * <p><b>AES-GCM</b> is authenticated encryption — a tampered ciphertext fails
 * to decrypt rather than silently producing garbage that would otherwise be
 * fed to {@link TotpService} as if it were a real secret. Each encryption
 * uses a fresh random 96-bit IV, stored alongside the ciphertext (GCM's IV is
 * not secret), which is why nonce reuse is never a concern here.
 */
@Component
public class MfaSecretCipher {

    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public MfaSecretCipher(JwtProperties jwtProperties) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] derivedKey = sha256.digest(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(derivedKey, KEY_ALGORITHM);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv).put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Could not encrypt MFA secret", ex);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] raw = Base64.getDecoder().decode(encoded);
            byte[] iv = Arrays.copyOfRange(raw, 0, GCM_IV_LENGTH_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(raw, GCM_IV_LENGTH_BYTES, raw.length);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Could not decrypt MFA secret", ex);
        }
    }
}
