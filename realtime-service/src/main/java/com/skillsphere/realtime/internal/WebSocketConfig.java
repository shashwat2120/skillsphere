package com.skillsphere.realtime.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * The live transport: one STOMP endpoint, a topic per arena.
 *
 * <p><b>Commands are REST, not STOMP.</b> Joining, starting, answering and
 * ending an arena are all plain authenticated REST calls — the same pattern
 * every other module in this codebase already uses, including
 * {@code CurrentUser.requireId()} for identity and standard exception
 * handling for validation. STOMP exists purely for the server to push state
 * out to every open connection at once: the next question, the leaderboard
 * after an answer, a confusion alert. A client that only ever needs to
 * {@code SUBSCRIBE} needs none of a full bidirectional STOMP command surface,
 * and the REST side gets to stay exactly as boring and well-tested as
 * everything else here — the realtime module earns its complexity budget
 * from the broadcast fan-out, not from reinventing request handling over a
 * different transport.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor authInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // No SockJS fallback: every browser this product targets speaks
        // native WebSocket, and SockJS's long-polling fallback would need
        // its own CSRF and session story for no benefit here.
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // /topic for fan-out broadcast; /app would carry client-to-server
        // commands if this module ever grows a @MessageMapping surface, but
        // nothing here uses it yet since commands are REST.
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
