package com.skillsphere.realtime.internal;

import com.skillsphere.identity.TokenAuthentication;
import com.skillsphere.shared.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * Authenticates the STOMP {@code CONNECT} frame from its {@code Authorization}
 * header — the WebSocket equivalent of {@code JwtAuthenticationFilter}, needed
 * because a handshake never passes through the servlet filter chain that
 * secures every other request.
 *
 * <p><b>A missing or invalid token does not reject the connection.</b> Arenas
 * explicitly allow guest participants with no account at all
 * ({@code allow_guests}), so CONNECT has to succeed either way — this only
 * decides whether the session carries a real identity afterward. Where that
 * matters (crediting a signed-in learner's score, an instructor-only topic),
 * the REST layer checks {@code CurrentUser} the same way it does everywhere
 * else; this interceptor's only job is making that possible for a WebSocket
 * session the way it already is for an HTTP one.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenAuthentication tokenAuthentication;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String header = accessor.getFirstNativeHeader("Authorization");
            if (header != null && header.startsWith(BEARER_PREFIX)) {
                tokenAuthentication.authenticate(header.substring(BEARER_PREFIX.length()).trim())
                        .ifPresentOrElse(
                                principal -> {
                                    accessor.setUser(new StompPrincipal(principal));
                                    log.debug("WebSocket session authenticated for user {}", principal.id());
                                },
                                () -> log.debug("WebSocket CONNECT presented an invalid or revoked token"));
            }
        }
        return message;
    }

    /** Adapts {@link UserPrincipal} to the {@link Principal} the STOMP session stores it as. */
    private record StompPrincipal(UserPrincipal principal) implements Principal {
        @Override
        public String getName() {
            return String.valueOf(principal.id());
        }
    }
}
