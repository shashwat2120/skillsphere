/**
 * Identity: JWT validation for requests this process still handles directly.
 *
 * <p>Account management, token issuance and everything that owns the
 * {@code users} table moved to identity-service — see that project's own
 * {@code identity} module, which this package used to be the whole of. What
 * remains here is deliberately narrow: this process still terminates HTTP
 * (and WebSocket) requests on its own, so it still needs to verify a bearer
 * token locally rather than calling identity-service on every request —
 * {@link com.skillsphere.identity.security.JwtAuthenticationFilter} and
 * {@link com.skillsphere.identity.TokenAuthentication} (used directly by
 * {@code realtime}'s STOMP handshake, which happens outside the servlet
 * filter chain) are that verification. Both trust the same
 * {@code skillsphere.security.jwt.secret} identity-service signs with — no
 * network call needed, only a shared secret and matching issuer, the
 * standard shape for stateless JWT validation across services.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Identity",
        allowedDependencies = {"shared"})
package com.skillsphere.identity;
