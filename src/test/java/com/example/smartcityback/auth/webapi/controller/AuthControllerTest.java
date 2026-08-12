package com.example.smartcityback.auth.webapi.controller;

import com.example.smartcityback.auth.infrastructure.jwt.JwtAuthDetails;
import com.example.smartcityback.auth.infrastructure.jwt.JwtTokenService;
import com.example.smartcityback.auth.infrastructure.token.RefreshTokenStore;
import com.example.smartcityback.auth.infrastructure.token.TokenBlacklist;
import com.example.smartcityback.auth.infrastructure.user.EmailAlreadyRegisteredException;
import com.example.smartcityback.auth.infrastructure.user.InMemoryUserRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = AuthController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class, OAuth2ClientAutoConfiguration.class}
)
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AuthenticationManager authManager;
    @MockitoBean JwtTokenService jwtTokenService;
    @MockitoBean RefreshTokenStore refreshTokenStore;
    @MockitoBean TokenBlacklist tokenBlacklist;
    @MockitoBean InMemoryUserRegistry userRegistry;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("returns 200 with a JWT, refresh token, and the ADMIN role for a valid login")
    void login_validCredentials_returns200WithTokenAndRole() throws Exception {
        var springAuth = new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        given(authManager.authenticate(any())).willReturn(springAuth);
        given(jwtTokenService.generate("admin", "ADMIN")).willReturn("jwt-token");
        given(refreshTokenStore.issue("admin", "ADMIN")).willReturn("refresh-token");

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "username": "admin", "password": "pass" }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
    }

    @Test
    @DisplayName("returns 401 for a login with the wrong password")
    void login_badCredentials_returns401() throws Exception {
        given(authManager.authenticate(any())).willThrow(new BadCredentialsException("bad"));

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "username": "admin", "password": "wrong" }
                            """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("defaults to the VIEWER role when Spring Security's authentication result carries no role "
            + "authority at all")
    void login_noRoleAuthority_defaultsToViewer() throws Exception {
        var springAuth = new UsernamePasswordAuthenticationToken("viewer", null, List.of());
        given(authManager.authenticate(any())).willReturn(springAuth);
        given(jwtTokenService.generate("viewer", "VIEWER")).willReturn("jwt-token");
        given(refreshTokenStore.issue("viewer", "VIEWER")).willReturn("refresh-token");

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "username": "viewer", "password": "pass" }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("VIEWER"));
    }

    @Test
    @DisplayName("returns 200 with a brand-new JWT and refresh token when redeeming a valid refresh token")
    void refresh_validToken_returns200WithNewTokens() throws Exception {
        var entry = new RefreshTokenStore.Entry("admin", "ADMIN", Long.MAX_VALUE);
        given(refreshTokenStore.consume("old-refresh")).willReturn(Optional.of(entry));
        given(jwtTokenService.generate("admin", "ADMIN")).willReturn("new-jwt");
        given(refreshTokenStore.issue("admin", "ADMIN")).willReturn("new-refresh");

        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "refreshToken": "old-refresh" }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("new-jwt"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh"));
    }

    @Test
    @DisplayName("returns 401 for a refresh token the store doesn't recognize")
    void refresh_invalidToken_returns401() throws Exception {
        given(refreshTokenStore.consume(anyString())).willReturn(Optional.empty());

        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "refreshToken": "bad-token" }
                            """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("returns 201 with a JWT, the default VIEWER role, and a refresh token for a newly registered email")
    void register_newEmail_returns201WithToken() throws Exception {
        given(jwtTokenService.generate(anyString(), anyString())).willReturn("jwt-token");
        given(refreshTokenStore.issue(anyString(), anyString())).willReturn("refresh-token");

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "email": "new@example.com", "password": "password123" }
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.role").value("VIEWER"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
    }

    @Test
    @DisplayName("returns 409 when registering an email that's already taken")
    void register_duplicateEmail_returns409() throws Exception {
        willThrow(new EmailAlreadyRegisteredException("existing@example.com"))
                .given(userRegistry).register(anyString(), anyString());

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "email": "existing@example.com", "password": "password123" }
                            """))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("rejects a registration request whose email isn't a valid email address")
    void register_invalidEmail_returns422() throws Exception {
        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "email": "not-an-email", "password": "password123" }
                            """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("rejects a registration request whose password is too short")
    void register_passwordTooShort_returns422() throws Exception {
        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "email": "user@example.com", "password": "short" }
                            """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("returns 204 and revokes both the access token's JTI and the refresh token on logout")
    void logout_validRequest_returns204() throws Exception {
        var mockAuth = new UsernamePasswordAuthenticationToken("admin", null, List.of());
        mockAuth.setDetails(new JwtAuthDetails("test-jti", System.currentTimeMillis() + 3_600_000));
        willDoNothing().given(tokenBlacklist).revoke(anyString(), any(long.class));
        given(refreshTokenStore.consume(anyString())).willReturn(Optional.empty());

        // authentication() post-processor requires Spring Security's context-loading filter,
        // which is absent when security auto-config is excluded. Set the context directly instead;
        // MockMvc processes synchronously so SecurityContextHolder is visible to the controller.
        mockMvc.perform(post("/v1/auth/logout")
                        .with(request -> {
                            var ctx = SecurityContextHolder.createEmptyContext();
                            ctx.setAuthentication(mockAuth);
                            SecurityContextHolder.setContext(ctx);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "refreshToken": "some-refresh-token" }
                            """))
                .andExpect(status().isNoContent());
    }
}
