/**
 * Identity: JWT validation for requests this process still handles directly.
 *
 * <p>Account management, token issuance and everything that owns the
 * {@code users} table moved to identity-service — see that project's own
 * {@code identity} module, which this package used to be the whole of. What
 * remains here is deliberately narrow: this process still terminates plain
 * HTTP requests on its own, so it still needs to verify a bearer token
 * locally rather than calling identity-service on every request —
 * {@link com.skillsphere.identity.security.JwtAuthenticationFilter} is that
 * verification. It trusts the same {@code skillsphere.security.jwt.secret}
 * identity-service signs with — no network call needed, only a shared secret
 * and matching issuer, the standard shape for stateless JWT validation
 * across services.
 *
 * <p>This module used to also expose {@code TokenAuthentication}, a way to
 * verify a token outside the servlet filter chain, for {@code realtime}'s
 * STOMP {@code CONNECT} frame. That interface and its implementation were
 * removed once {@code realtime} was extracted to its own service (which
 * carries an identical copy for the same purpose) and nothing in this
 * process needed WebSocket auth of its own any more.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Identity",
        allowedDependencies = {"shared"})
package com.skillsphere.identity;
