package com.skillsphere.identity.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.skillsphere.shared.security.UserPrincipal;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates a request from its bearer token.
 *
 * <p>Runs on every request and does three things in order: verify the
 * signature, check the denylist, and populate the security context. Only the
 * denylist check touches Redis; everything else is local, which is what keeps
 * an authenticated request free of database work.
 *
 * <p><b>An invalid token never rejects the request here.</b> The filter simply
 * leaves the context unauthenticated and continues the chain, letting the
 * authorisation rules decide. That matters because many endpoints are public —
 * a stale token in an old browser tab must not turn the public catalogue into a
 * 401 — and because rejecting inside a filter bypasses the exception handler
 * and produces an error shape that differs from every other response.
 *
 * <p>Roles arrive from the token rather than from a user lookup. It is the
 * deliberate trade behind stateless auth: a role change takes effect when the
 * access token next refreshes, up to fifteen minutes later. Where that is not
 * acceptable — suspension, in particular — the denylist provides immediate
 * revocation instead.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final TokenDenylist denylist;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {

        extractToken(request)
                .flatMap(jwtService::parseAccessToken)
                .filter(claims -> {
                    if (denylist.isDenied(claims.tokenId(), claims.userId(), claims.issuedAt())) {
                        log.debug("Denied revoked token for user {}", claims.userId());
                        return false;
                    }
                    return true;
                })
                .ifPresent(claims -> authenticate(claims, request));

        chain.doFilter(request, response);
    }

    private void authenticate(JwtService.AccessTokenClaims claims, HttpServletRequest request) {
        // Exposed so logout can deny this exact token without re-parsing it.
        // Kept as request attributes rather than stuffed into the principal,
        // which other modules read and which should carry identity only.
        request.setAttribute("jwt.tokenId", claims.tokenId());
        request.setAttribute("jwt.expiresAt", claims.expiresAt());

        List<SimpleGrantedAuthority> authorities = claims.roles().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

        UserPrincipal principal = new UserPrincipal(claims.userId(), claims.email(), claims.roles());

        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private java.util.Optional<String> extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            return token.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(token);
        }
        return java.util.Optional.empty();
    }

    /**
     * Skips the filter entirely for endpoints that can never be authenticated.
     *
     * <p>Login and registration in particular: running a denylist lookup against
     * Redis for a request that carries no token is pure latency on the two
     * endpoints most exposed to unauthenticated traffic, which is precisely
     * where a flood would land.
     */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/api/auth/login")
                || path.startsWith("/api/auth/register")
                || path.startsWith("/api/auth/refresh")
                || path.startsWith("/actuator/health");
    }
}
