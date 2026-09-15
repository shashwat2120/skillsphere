package com.skillsphere.shared.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * A Redis-backed fixed-window rate limiter.
 *
 * <p><b>Why not a library.</b> Bucket4j is the usual answer and is a fine
 * library, but its distributed mode pulls in another integration to configure
 * and operate for behaviour that is twenty lines of Redis. Fewer moving parts
 * is worth more here than a token bucket's smoother shaping.
 *
 * <p><b>Fixed window, and what that costs.</b> A counter is incremented per key
 * and expires after the window. The known weakness is the boundary: a caller can
 * spend a full quota at the end of one window and another immediately at the
 * start of the next, so a limit of five per minute tolerates ten across two
 * adjacent seconds. A sliding-window log fixes that and costs a sorted set plus
 * a trim on every call.
 *
 * <p>That trade is acceptable because this limiter defends a login endpoint,
 * where the threat is thousands of attempts per minute rather than ten. Credential
 * stuffing is not stopped by the last increment of a window — it is stopped by
 * the account lockout sitting behind this, which counts failures rather than
 * requests.
 *
 * <p><b>Atomicity.</b> {@code INCR} is atomic, so concurrent requests cannot
 * both read a stale count. The expiry is only set when the counter is first
 * created; setting it on every increment would slide the window forward
 * indefinitely and the limit would never reset.
 *
 * <p><b>On failure the request is allowed.</b> A Redis outage must not take the
 * whole platform offline, so the limiter degrades to no limiting rather than to
 * refusing everyone.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiter {

    private static final String KEY_PREFIX = "ratelimit:";

    private final StringRedisTemplate redis;

    /**
     * Counts one hit against a key.
     *
     * @param key    identifies the thing being limited, e.g. {@code login:ip:1.2.3.4}
     * @param limit  permitted hits per window
     * @param window length of the window
     * @return the outcome, including how long to wait when the limit is hit
     */
    public Decision check(String key, int limit, Duration window) {
        String redisKey = KEY_PREFIX + key;
        try {
            Long count = redis.opsForValue().increment(redisKey);
            if (count == null) {
                return Decision.allowed(limit);
            }

            // Only the first hit establishes the window. Re-setting the TTL on
            // every request would keep pushing the expiry out, and a caller
            // making steady requests would never see the counter reset.
            if (count == 1L) {
                redis.expire(redisKey, window);
            }

            if (count > limit) {
                Long ttl = redis.getExpire(redisKey);
                // A missing TTL means the key somehow lost its expiry; restore
                // it rather than leaving a counter stuck above the limit forever.
                if (ttl == null || ttl < 0) {
                    redis.expire(redisKey, window);
                    ttl = window.toSeconds();
                }
                return Decision.denied(Duration.ofSeconds(ttl));
            }

            return Decision.allowed(limit - count.intValue());

        } catch (Exception ex) {
            log.error("Rate limit check failed for {} — allowing request", key, ex);
            return Decision.allowed(limit);
        }
    }

    /**
     * Reads a counter without incrementing it.
     *
     * <p>Needed wherever the decision to count comes <em>after</em> the decision
     * to allow. Login is the case that matters: whether an attempt counts as a
     * failure is unknown until the password has been checked, so the lockout
     * must be able to ask "is this account already locked?" without the question
     * itself pushing the account closer to lockout.
     */
    public Decision peek(String key, int limit) {
        try {
            String value = redis.opsForValue().get(KEY_PREFIX + key);
            if (value == null) {
                return Decision.allowed(limit);
            }
            long count = Long.parseLong(value);
            if (count >= limit) {
                Long ttl = redis.getExpire(KEY_PREFIX + key);
                return Decision.denied(Duration.ofSeconds(ttl == null || ttl < 0 ? 0 : ttl));
            }
            return Decision.allowed(limit - (int) count);
        } catch (Exception ex) {
            log.error("Rate limit peek failed for {} — allowing request", key, ex);
            return Decision.allowed(limit);
        }
    }

    /** Clears a counter, used when an attempt succeeds and the penalty no longer applies. */
    public void reset(String key) {
        try {
            redis.delete(KEY_PREFIX + key);
        } catch (Exception ex) {
            log.error("Could not reset rate limit key {}", key, ex);
        }
    }

    /**
     * @param allowed   whether the caller may proceed
     * @param remaining hits left in this window
     * @param retryAfter how long until the window resets, when denied
     */
    public record Decision(boolean allowed, int remaining, Duration retryAfter) {

        static Decision allowed(int remaining) {
            return new Decision(true, Math.max(remaining, 0), Duration.ZERO);
        }

        static Decision denied(Duration retryAfter) {
            return new Decision(false, 0, retryAfter);
        }
    }
}
