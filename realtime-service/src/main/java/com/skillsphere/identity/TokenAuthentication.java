package com.skillsphere.identity;

import com.skillsphere.shared.security.UserPrincipal;

import java.util.Optional;

/**
 * Identity's public API for authenticating a bearer token outside the normal
 * servlet filter chain.
 *
 * <p>Exists specifically for realtime's STOMP {@code CONNECT} frame, which
 * carries a token but never passes through {@code JwtAuthenticationFilter} —
 * WebSocket handshakes and STOMP frames are not HTTP requests once the
 * connection has upgraded. Rather than have realtime depend on
 * {@code identity.security} internals (JWT parsing, the denylist, the
 * signing key) to reimplement what the filter already does, this exposes the
 * same verification identity already trusts for every other request:
 * signature, issuer, expiry, and a live denylist check — a revoked token must
 * not be able to open a live connection just because it can still open a
 * WebSocket handshake.
 */
public interface TokenAuthentication {

    /** Empty if the token is missing, malformed, expired, or has been revoked. */
    Optional<UserPrincipal> authenticate(String bearerToken);
}
