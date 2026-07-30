package com.example.smartcityback.auth.webapi.filter;

import com.example.smartcityback.auth.infrastructure.jwt.JwtTokenService;
import com.example.smartcityback.auth.infrastructure.token.TokenBlacklist;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class StompAuthChannelInterceptorTest {

    private JwtTokenService jwtTokenService;
    private TokenBlacklist tokenBlacklist;
    private StompAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtTokenService = mock(JwtTokenService.class);
        tokenBlacklist = mock(TokenBlacklist.class);
        interceptor = new StompAuthChannelInterceptor(jwtTokenService, tokenBlacklist);
    }

    private static Message<byte[]> connectFrame(String authorizationHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorizationHeader != null) {
            accessor.setNativeHeader("Authorization", authorizationHeader);
        }
        // Mirrors StompSubProtocolHandler, which leaves inbound frames mutable so that
        // preSend interceptors (like the one under test) can call accessor.setUser(...).
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void connectWithoutAuthorizationHeader_isRejected() {
        Message<byte[]> frame = connectFrame(null);

        assertThatThrownBy(() -> interceptor.preSend(frame, null))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void connectWithInvalidToken_isRejected() {
        given(jwtTokenService.validate(anyString())).willThrow(new JwtException("bad signature"));
        Message<byte[]> frame = connectFrame("Bearer invalid-token");

        assertThatThrownBy(() -> interceptor.preSend(frame, null))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void connectWithRevokedToken_isRejected() {
        Claims claims = mock(Claims.class);
        given(claims.getId()).willReturn("jti-revoked");
        given(jwtTokenService.validate(anyString())).willReturn(claims);
        given(tokenBlacklist.isRevoked("jti-revoked")).willReturn(true);
        Message<byte[]> frame = connectFrame("Bearer revoked-token");

        assertThatThrownBy(() -> interceptor.preSend(frame, null))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void connectWithValidToken_setsStompUser() {
        Claims claims = mock(Claims.class);
        given(claims.getId()).willReturn("jti-valid");
        given(claims.getSubject()).willReturn("admin");
        given(claims.get("role", String.class)).willReturn("ADMIN");
        given(jwtTokenService.validate(anyString())).willReturn(claims);
        given(tokenBlacklist.isRevoked("jti-valid")).willReturn(false);
        Message<byte[]> frame = connectFrame("Bearer valid-token");

        interceptor.preSend(frame, null);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(frame);
        assertThat(accessor.getUser()).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(accessor.getUser().getName()).isEqualTo("admin");
    }

    @Test
    void nonConnectFrame_isPassedThroughWithoutAuth() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Message<byte[]> frame = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(frame, null);

        assertThat(result).isSameAs(frame);
    }

    @Test
    void connectWithMalformedAuthorizationHeader_isRejected() {
        Message<byte[]> frame = connectFrame("Basic xyz");

        assertThatThrownBy(() -> interceptor.preSend(frame, null))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void nonStompMessage_isPassedThroughWithoutAuth() {
        Message<byte[]> frame = MessageBuilder.withPayload(new byte[0]).build();

        Message<?> result = interceptor.preSend(frame, null);

        assertThat(result).isSameAs(frame);
    }
}
