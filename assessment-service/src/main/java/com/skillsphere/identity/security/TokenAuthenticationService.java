package com.skillsphere.identity.security;

import com.skillsphere.identity.TokenAuthentication;
import com.skillsphere.shared.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Implements identity's public token-authentication API.
 *
 * <p>The same three checks {@link JwtAuthenticationFilter} runs on every HTTP
 * request — signature and issuer, token type, denylist — applied to a token
 * handed in directly rather than read off a servlet request. Kept as its own
 * class rather than folded into the filter so neither has to know the other
 * exists; they happen to share logic, not a caller relationship.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenAuthenticationService implements TokenAuthentication {

    private final JwtService jwtService;
    private final TokenDenylist denylist;

    @Override
    public Optional<UserPrincipal> authenticate(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            return Optional.empty();
        }

        return jwtService.parseAccessToken(bearerToken)
                .filter(claims -> {
                    if (denylist.isDenied(claims.tokenId(), claims.userId(), claims.issuedAt())) {
                        log.debug("Denied revoked token for user {} on WebSocket connect", claims.userId());
                        return false;
                    }
                    return true;
                })
                .map(claims -> new UserPrincipal(claims.userId(), claims.email(), claims.roles()));
    }
}
