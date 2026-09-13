package com.skillsphere.identity.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and verifies access tokens.
 *
 * <p>Access tokens are signed, short-lived and stateless. They carry the
 * caller's id, roles and a unique {@code jti}, so an ordinary authenticated
 * request needs no database lookup at all — which is what lets the Sprint 6
 * gateway authorise traffic without calling the identity service.
 *
 * <p>The {@code jti} is not decoration. It is the handle the Redis denylist
 * uses to revoke a specific token before it expires, which is the answer to
 * stateless JWT's one real weakness.
 *
 * <p>Refresh tokens are deliberately <em>not</em> JWTs. They are opaque random
 * strings stored hashed in the database, because a refresh token must be
 * revocable with certainty and must support reuse detection — neither of which
 * a self-contained signed token can offer.
 */
@Slf4j
@Service
public class JwtService {

    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TOKEN_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Issues an access token for an authenticated account.
     *
     * @return the compact token and the {@code jti} that identifies it, so the
     *         caller can record or later revoke this exact token
     */
    public IssuedToken issueAccessToken(Long userId, String email, List<String> roles) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.accessTokenTtl());
        String tokenId = UUID.randomUUID().toString();

        String token = Jwts.builder()
                .id(tokenId)
                .subject(String.valueOf(userId))
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim(CLAIM_ROLES, roles)
                .claim(CLAIM_TOKEN_TYPE, TYPE_ACCESS)
                // The email is included for logging and display only. Nothing
                // authorises off it — authorisation uses the subject and roles,
                // because an address can be changed and is not an identity.
                .claim("email", email)
                .signWith(signingKey)
                .compact();

        return new IssuedToken(token, tokenId, expiry);
    }

    /**
     * Verifies signature, issuer, expiry and token type.
     *
     * <p>Returns empty rather than throwing, because an invalid token is an
     * ordinary event on a public endpoint — expired sessions, stale tabs,
     * probing scanners — and not an exceptional one.
     */
    public Optional<AccessTokenClaims> parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // Reject a refresh-shaped token presented as an access token. Without
            // this check, any token signed by the same key would be accepted
            // anywhere, and token-type confusion is a well-trodden attack.
            if (!TYPE_ACCESS.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
                log.debug("Rejected token with unexpected type");
                return Optional.empty();
            }

            @SuppressWarnings("unchecked")
            List<String> roles = claims.get(CLAIM_ROLES, List.class);

            return Optional.of(new AccessTokenClaims(
                    Long.valueOf(claims.getSubject()),
                    claims.getId(),
                    claims.get("email", String.class),
                    roles == null ? List.of() : roles,
                    claims.getExpiration().toInstant()));

        } catch (JwtException | IllegalArgumentException ex) {
            // Deliberately terse: the message can echo attacker-supplied input,
            // and a failed parse tells us nothing worth an error-level log.
            log.debug("Access token rejected: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public record IssuedToken(String token, String tokenId, Instant expiresAt) {}

    public record AccessTokenClaims(
            Long userId,
            String tokenId,
            String email,
            List<String> roles,
            Instant expiresAt) {}
}
