package com.example.smartcityback.auth.webapi.filter;

import com.example.smartcityback.auth.infrastructure.JwtTokenService;
import com.example.smartcityback.auth.infrastructure.TokenBlacklist;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JwtAuthFilterTest {

    private JwtTokenService jwtTokenService;
    private TokenBlacklist tokenBlacklist;
    private JwtAuthFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        jwtTokenService = mock(JwtTokenService.class);
        tokenBlacklist = mock(TokenBlacklist.class);
        filter = new JwtAuthFilter(jwtTokenService, tokenBlacklist);
        chain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingAuthorizationHeader_chainsWithoutSettingAuth() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void invalidToken_chainsWithoutSettingAuth() throws Exception {
        given(jwtTokenService.validate(anyString())).willThrow(new JwtException("bad signature"));
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-token");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void revokedToken_chainsWithoutSettingAuth() throws Exception {
        Claims claims = mock(Claims.class);
        given(claims.getId()).willReturn("jti-revoked");
        given(jwtTokenService.validate(anyString())).willReturn(claims);
        given(tokenBlacklist.isRevoked("jti-revoked")).willReturn(true);

        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer revoked-token");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void validToken_setsAuthenticationInContext() throws Exception {
        Claims claims = mock(Claims.class);
        given(claims.getId()).willReturn("jti-valid");
        given(claims.getSubject()).willReturn("admin");
        given(claims.get("role", String.class)).willReturn("ADMIN");
        given(claims.getExpiration()).willReturn(new Date(System.currentTimeMillis() + 3_600_000));
        given(jwtTokenService.validate(anyString())).willReturn(claims);
        given(tokenBlacklist.isRevoked("jti-valid")).willReturn(false);

        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("admin");
    }
}
