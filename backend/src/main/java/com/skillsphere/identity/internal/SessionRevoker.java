package com.skillsphere.identity.internal;

import com.skillsphere.identity.domain.RefreshToken;
import com.skillsphere.identity.domain.RefreshTokenRepository;
import com.skillsphere.identity.security.JwtProperties;
import com.skillsphere.identity.security.TokenDenylist;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Revokes every session for an account, in its own transaction.
 *
 * <p><b>Why this exists as a separate bean.</b> Revocation is nearly always
 * followed by rejecting the request that triggered it, and that rejection is an
 * exception — which rolls back the caller's transaction and silently undoes the
 * revocation with it. The security response cancels itself and the logs show a
 * warning for something that never actually happened.
 *
 * <p>{@code REQUIRES_NEW} makes the revocation commit independently, so it
 * survives the rollback. It has to live in a separate bean because Spring's
 * proxying means a self-invoked {@code @Transactional} method runs in the
 * caller's transaction and the propagation setting is ignored entirely — a
 * failure mode that looks like the annotation simply not working.
 *
 * <p>This was caught by an end-to-end test: reuse detection correctly returned
 * 401, and the supposedly burned token kept working afterwards.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionRevoker {

    private final RefreshTokenRepository refreshTokens;
    private final TokenDenylist denylist;
    private final JwtProperties jwtProperties;

    /**
     * Revokes all refresh tokens for a user and denies their outstanding access
     * tokens.
     *
     * <p>Both halves are required. Revoking refresh tokens alone leaves any
     * issued access token working until it expires, which on a fifteen-minute
     * lifetime is a long time to leave a thief signed in.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllSessions(Long userId, RefreshToken.RevocationReason reason) {
        int revoked = refreshTokens.revokeAllForUser(
                userId, Instant.now(), reason.name().toLowerCase());

        denylist.denyAllForUser(userId, jwtProperties.accessTokenTtl());

        log.warn("Revoked {} refresh token(s) and denied access tokens for user {} — reason {}",
                revoked, userId, reason);
    }
}
