package com.example.smartcityback.auth.webapi.handler;

import com.example.smartcityback.auth.infrastructure.jwt.JwtTokenService;
import com.example.smartcityback.auth.infrastructure.token.RefreshTokenStore;
import com.example.smartcityback.auth.infrastructure.user.InMemoryUserRegistry;
import com.example.smartcityback.auth.infrastructure.user.RegisteredUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class OAuth2SuccessHandlerTest {

    private final InMemoryUserRegistry registry = mock(InMemoryUserRegistry.class);
    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final RefreshTokenStore refreshTokenStore = mock(RefreshTokenStore.class);

    private OAuth2SuccessHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OAuth2SuccessHandler(registry, jwtTokenService, refreshTokenStore,
                3_600_000L, "http://localhost:4200/callback");
    }

    @Test
    void onAuthenticationSuccess_newGoogleUser_redirectsToCallbackWithFragment() throws Exception {
        OAuth2User oAuth2User = mock(OAuth2User.class);
        given(oAuth2User.getAttribute("email")).willReturn("new@example.com");
        Authentication authentication = mock(Authentication.class);
        given(authentication.getPrincipal()).willReturn(oAuth2User);

        RegisteredUser user = new RegisteredUser("new@example.com", null, "VIEWER", RegisteredUser.AuthProvider.GOOGLE);
        given(registry.findOrRegisterOAuth("new@example.com")).willReturn(user);
        given(jwtTokenService.generate("new@example.com", "VIEWER")).willReturn("jwt-token");
        given(refreshTokenStore.issue("new@example.com", "VIEWER")).willReturn("refresh-token");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        String redirectedUrl = response.getRedirectedUrl();
        assertThat(redirectedUrl).startsWith("http://localhost:4200/callback#");
        assertThat(redirectedUrl).contains("token=jwt-token");
        assertThat(redirectedUrl).contains("role=VIEWER");
        assertThat(redirectedUrl).contains("expiresInMs=3600000");
        assertThat(redirectedUrl).contains("refreshToken=refresh-token");
    }

    @Test
    void onAuthenticationSuccess_existingAdminUser_redirectsWithAdminRole() throws Exception {
        OAuth2User oAuth2User = mock(OAuth2User.class);
        given(oAuth2User.getAttribute("email")).willReturn("admin@example.com");
        Authentication authentication = mock(Authentication.class);
        given(authentication.getPrincipal()).willReturn(oAuth2User);

        RegisteredUser user = new RegisteredUser("admin@example.com", null, "ADMIN", RegisteredUser.AuthProvider.GOOGLE);
        given(registry.findOrRegisterOAuth("admin@example.com")).willReturn(user);
        given(jwtTokenService.generate("admin@example.com", "ADMIN")).willReturn("admin-token");
        given(refreshTokenStore.issue("admin@example.com", "ADMIN")).willReturn("admin-refresh");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).contains("role=ADMIN");
        assertThat(response.getRedirectedUrl()).contains("token=admin-token");
    }

    @Test
    void onAuthenticationSuccess_tokenAppearsInFragmentNotQueryParam() throws Exception {
        OAuth2User oAuth2User = mock(OAuth2User.class);
        given(oAuth2User.getAttribute("email")).willReturn("user@example.com");
        Authentication authentication = mock(Authentication.class);
        given(authentication.getPrincipal()).willReturn(oAuth2User);

        RegisteredUser user = new RegisteredUser("user@example.com", null, "VIEWER", RegisteredUser.AuthProvider.GOOGLE);
        given(registry.findOrRegisterOAuth("user@example.com")).willReturn(user);
        given(jwtTokenService.generate("user@example.com", "VIEWER")).willReturn("jwt-token");
        given(refreshTokenStore.issue("user@example.com", "VIEWER")).willReturn("refresh-token");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        String redirectedUrl = response.getRedirectedUrl();
        // Fragment (#) must appear before the credentials — token must not be a query param (?)
        assertThat(redirectedUrl).contains("#");
        assertThat(redirectedUrl.indexOf('#')).isLessThan(redirectedUrl.indexOf("token="));
        assertThat(redirectedUrl).doesNotContain("?token=");
    }
}
