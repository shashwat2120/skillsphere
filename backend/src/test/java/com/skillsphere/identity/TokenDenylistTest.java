package com.skillsphere.identity;

import com.skillsphere.identity.security.TokenDenylist;
import com.skillsphere.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the revocation boundary deterministically.
 *
 * <p><b>Why this exists separately from the end-to-end test.</b> The flow test
 * was written first and was worthless: it asserted that an access token is
 * refused after a revocation, and it passed even with the bug deliberately
 * reintroduced. Argon2id at 64 MiB takes roughly a quarter of a second, so login
 * and revocation land in different seconds and the off-by-one boundary is simply
 * never reached. A regression test that only catches its bug when the timing
 * happens to cooperate is worse than no test, because it reports safety it
 * cannot deliver.
 *
 * <p>These tests construct the exact instant instead of hoping to land on it,
 * so the boundary is exercised every run. They were checked by reintroducing
 * the original {@code <} comparison and confirming the same-second case fails.
 */
class TokenDenylistTest extends IntegrationTest {

    @Autowired
    TokenDenylist denylist;

    @Autowired
    StringRedisTemplate redis;

    @MockitoBean
    JavaMailSender mailSender;

    private static final Duration TTL = Duration.ofMinutes(15);

    private Long freshUserId() {
        // Random so parallel or repeated runs cannot collide on a cutoff key.
        return Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000L) + 1;
    }

    @Test
    @DisplayName("a token issued in the SAME SECOND as the revocation is denied")
    void sameSecondTokenIsDenied() {
        Long userId = freshUserId();

        denylist.denyAllForUser(userId, TTL);

        // Read back the cutoff the implementation actually stored and build a
        // token issued at precisely that second — the case that shipped broken.
        String cutoff = redis.opsForValue().get("denylist:user:" + userId);
        assertThat(cutoff).as("cutoff should have been written").isNotNull();
        Instant issuedAtSameSecond = Instant.ofEpochSecond(Long.parseLong(cutoff));

        assertThat(denylist.isDenied("some-jti", userId, issuedAtSameSecond))
                .as("a JWT iat has one-second resolution, so a token minted in the "
                        + "same second as a revocation must still be refused — this is "
                        + "the window an automated token theft occupies")
                .isTrue();
    }

    @Test
    @DisplayName("a token issued before the revocation is denied")
    void earlierTokenIsDenied() {
        Long userId = freshUserId();
        denylist.denyAllForUser(userId, TTL);

        String cutoff = redis.opsForValue().get("denylist:user:" + userId);
        Instant earlier = Instant.ofEpochSecond(Long.parseLong(cutoff)).minusSeconds(30);

        assertThat(denylist.isDenied("some-jti", userId, earlier)).isTrue();
    }

    @Test
    @DisplayName("a token issued after the revocation is allowed, so re-login works")
    void laterTokenIsAllowed() {
        Long userId = freshUserId();
        denylist.denyAllForUser(userId, TTL);

        String cutoff = redis.opsForValue().get("denylist:user:" + userId);
        Instant later = Instant.ofEpochSecond(Long.parseLong(cutoff)).plusSeconds(2);

        // The boundary must not be so aggressive that signing in again is
        // impossible — revocation ends sessions, not the account.
        assertThat(denylist.isDenied("some-jti", userId, later)).isFalse();
    }

    @Test
    @DisplayName("an untouched user is not denied")
    void unrevokedUserIsAllowed() {
        assertThat(denylist.isDenied("some-jti", freshUserId(), Instant.now())).isFalse();
    }

    @Test
    @DisplayName("a specific token can be denied by its jti")
    void singleTokenDenied() {
        Long userId = freshUserId();
        String jti = UUID.randomUUID().toString();

        assertThat(denylist.isDenied(jti, userId, Instant.now())).isFalse();

        denylist.denyToken(jti, Instant.now().plus(TTL));

        assertThat(denylist.isDenied(jti, userId, Instant.now())).isTrue();
        // Denying one token must not sign the user out everywhere.
        assertThat(denylist.isDenied("a-different-jti", userId, Instant.now())).isFalse();
    }

    @Test
    @DisplayName("denying an already-expired token writes nothing")
    void expiredTokenNotStored() {
        String jti = UUID.randomUUID().toString();

        // A negative TTL would be rejected by Redis, and storing it would serve
        // no purpose — the token is already refused on expiry alone.
        denylist.denyToken(jti, Instant.now().minusSeconds(60));

        assertThat(redis.hasKey("denylist:jti:" + jti)).isFalse();
    }

    @Test
    @DisplayName("the per-token entry expires with the token rather than accumulating")
    void tokenEntryCarriesTtl() {
        String jti = UUID.randomUUID().toString();
        denylist.denyToken(jti, Instant.now().plusSeconds(120));

        Long ttl = redis.getExpire("denylist:jti:" + jti);
        // Bounded by the token's own life, which is what stops the denylist
        // growing without limit.
        assertThat(ttl).isNotNull().isGreaterThan(0L).isLessThanOrEqualTo(120L);
    }
}
