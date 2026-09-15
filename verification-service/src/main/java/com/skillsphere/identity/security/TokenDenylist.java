package com.skillsphere.identity.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Makes stateless access tokens revocable.
 *
 * <p>This closes JWT's one genuine weakness. A signed token is valid until it
 * expires, and nothing about verifying it consults a database — which is what
 * makes it fast, and also what makes "log this person out now" impossible by
 * default. A suspended instructor who keeps a valid token for another fourteen
 * minutes is not suspended in any sense a user would recognise.
 *
 * <p>Two kinds of revocation, because they answer different questions.
 *
 * <p><b>Per token.</b> Logout denies that specific {@code jti}. The Redis key
 * carries a TTL equal to the token's remaining life, so entries evict
 * themselves exactly when they stop mattering — the denylist can never grow
 * beyond the number of tokens currently alive, which is what stops this
 * becoming an ever-expanding table.
 *
 * <p><b>Per user, by cutoff.</b> Password change, suspension and reuse
 * detection must invalidate every token a person holds, including ones this
 * server has never seen. Enumerating them is impossible, so instead a cutoff
 * timestamp is recorded and any token issued before it is rejected. One key,
 * one comparison, and it covers tokens issued on other instances.
 *
 * <p><b>If Redis is unavailable</b> the request is allowed through rather than
 * blocked. That is a deliberate trade and worth stating plainly: failing open
 * means a Redis outage briefly extends the life of revoked tokens, while
 * failing closed would mean a Redis outage logs out every user of the platform
 * at once. Given access tokens live fifteen minutes, the exposure is bounded
 * and much smaller than the alternative outage.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenDenylist {

    private static final String TOKEN_KEY_PREFIX = "denylist:jti:";
    private static final String USER_CUTOFF_PREFIX = "denylist:user:";

    private final StringRedisTemplate redis;

    /** Denies a single token for whatever life it has left. */
    public void denyToken(String tokenId, Instant expiresAt) {
        Duration remaining = Duration.between(Instant.now(), expiresAt);
        if (remaining.isNegative() || remaining.isZero()) {
            return; // already expired; nothing to deny
        }
        try {
            redis.opsForValue().set(TOKEN_KEY_PREFIX + tokenId, "1", remaining);
        } catch (Exception ex) {
            log.error("Could not deny token {} — it stays valid until expiry", tokenId, ex);
        }
    }

    /**
     * Invalidates every token issued to a user before now.
     *
     * <p>The cutoff is kept slightly longer than the access-token lifetime so it
     * cannot expire while a token it was meant to block is still alive.
     */
    public void denyAllForUser(Long userId, Duration accessTokenTtl) {
        try {
            redis.opsForValue().set(
                    USER_CUTOFF_PREFIX + userId,
                    String.valueOf(Instant.now().getEpochSecond()),
                    accessTokenTtl.plusMinutes(5));
        } catch (Exception ex) {
            log.error("Could not set revocation cutoff for user {}", userId, ex);
        }
    }

    /**
     * @param tokenId  the {@code jti}
     * @param userId   the subject
     * @param issuedAt when the token was issued
     * @return true if this token must be refused
     */
    public boolean isDenied(String tokenId, Long userId, Instant issuedAt) {
        try {
            if (Boolean.TRUE.equals(redis.hasKey(TOKEN_KEY_PREFIX + tokenId))) {
                return true;
            }
            String cutoff = redis.opsForValue().get(USER_CUTOFF_PREFIX + userId);
            // Note <= rather than <. A JWT's iat claim has one-second
            // granularity, so a token issued in the same second as the
            // revocation would otherwise survive it — and automated token theft
            // plays out in milliseconds, which makes that exact second the one
            // that matters. Erring the other way costs at most one token issued
            // moments after a revocation, which a fresh login replaces
            // immediately; erring the way this originally did leaves a stolen
            // token live for its full lifetime.
            //
            // Found by an end-to-end test where iat and the cutoff landed on the
            // same second and the supposedly revoked token kept working.
            if (cutoff != null && issuedAt.getEpochSecond() <= Long.parseLong(cutoff)) {
                return true;
            }
            return false;
        } catch (Exception ex) {
            // See class notes: fail open. A Redis outage must not sign out the
            // entire platform.
            log.error("Denylist check failed for user {} — allowing request", userId, ex);
            return false;
        }
    }
}
