package com.example.smartcityback.auth.webapi.filter;

import com.example.smartcityback.auth.infrastructure.jwt.JwtTokenService;
import com.example.smartcityback.auth.infrastructure.token.TokenBlacklist;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Authenticates STOMP CONNECT frames.
 *
 * The /ws HTTP handshake is permitAll (browsers cannot attach a custom Authorization
 * header to a WebSocket/SockJS handshake request). Auth instead happens on the first
 * STOMP frame sent over the already-open socket — unlike the handshake, a STOMP frame
 * is an application-level message and can carry arbitrary headers.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenService jwtTokenService;
    private final TokenBlacklist tokenBlacklist;

    public StompAuthChannelInterceptor(JwtTokenService jwtTokenService, TokenBlacklist tokenBlacklist) {
        this.jwtTokenService = jwtTokenService;
        this.tokenBlacklist = tokenBlacklist;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
        }

        return message;
    }

    private UsernamePasswordAuthenticationToken authenticate(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new BadCredentialsException("Missing Authorization header on STOMP CONNECT");
        }

        String token = header.substring("Bearer ".length());
        try {
            Claims claims = jwtTokenService.validate(token);
            if (tokenBlacklist.isRevoked(claims.getId())) {
                throw new BadCredentialsException("Revoked token");
            }

            String role = claims.get("role", String.class);
            return new UsernamePasswordAuthenticationToken(
                    claims.getSubject(),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role))
            );
        } catch (JwtException e) {
            throw new BadCredentialsException("Invalid or expired token", e);
        }
    }
}
