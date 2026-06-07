package com.gridapp.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * STOMP channel interceptor that authenticates WebSocket connections.
 *
 * On each CONNECT frame, reads the Bearer token from the Authorization header,
 * decodes it via JwtDecoder, and sets the resulting Authentication as the
 * STOMP session's user principal. All subsequent messages in that session
 * carry this principal, which WebSocket handlers can read via headerAccessor.getUser().
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    Jwt jwt = jwtDecoder.decode(token);
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(jwt, null, Collections.emptyList());
                    accessor.setUser(auth);
                    log.debug("WebSocket CONNECT authenticated: sub={}", jwt.getSubject());
                } catch (Exception e) {
                    log.warn("WebSocket CONNECT: invalid JWT, session will be anonymous. error={}", e.getMessage());
                }
            }
        }
        return message;
    }
}
