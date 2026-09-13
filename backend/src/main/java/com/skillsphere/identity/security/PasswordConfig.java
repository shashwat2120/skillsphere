package com.skillsphere.identity.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

/**
 * Password hashing.
 *
 * <p><b>Argon2id over BCrypt.</b> BCrypt is compute-hard but cheap in memory,
 * which is exactly the shape a GPU attacks well — thousands of parallel guesses
 * at little cost. Argon2id is memory-hard: every guess must allocate real
 * memory, so parallel hardware loses most of its advantage. It won the Password
 * Hashing Competition and is the current recommendation.
 *
 * <p><b>Parameters.</b> 64 MiB, 3 iterations, 4 lanes — the OWASP baseline.
 * These are a deliberate trade, not a magic number: raising memory hardens each
 * hash but multiplies cost under concurrent logins, because every simultaneous
 * authentication holds its own 64 MiB. The right value is the largest that keeps
 * hashing near 250 ms on the actual production hardware, so it should be
 * measured there rather than assumed. Too high is its own vulnerability — a
 * login endpoint that allocates gigabytes under load is a denial-of-service
 * target.
 *
 * <p><b>Why delegating.</b> Hashes are stored with an {@code {argon2}} prefix,
 * so the algorithm can be replaced later while existing hashes still verify and
 * upgrade transparently as people log in. Pinning a bare encoder would mean any
 * future improvement invalidates every stored password at once.
 */
@Configuration
public class PasswordConfig {

    private static final String DEFAULT_ENCODER = "argon2";

    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;
    private static final int PARALLELISM = 4;
    private static final int MEMORY_KB = 65536;   // 64 MiB
    private static final int ITERATIONS = 3;

    @Bean
    public PasswordEncoder passwordEncoder() {
        Argon2PasswordEncoder argon2 = new Argon2PasswordEncoder(
                SALT_LENGTH, HASH_LENGTH, PARALLELISM, MEMORY_KB, ITERATIONS);

        // Argon2id encodes every new password. BCrypt stays in the map purely so
        // that a hash imported or seeded in that format still verifies and is
        // then re-encoded on next login.
        Map<String, PasswordEncoder> encoders = Map.of(
                DEFAULT_ENCODER, argon2,
                "bcrypt", new BCryptPasswordEncoder());

        return new DelegatingPasswordEncoder(DEFAULT_ENCODER, encoders);
    }
}
