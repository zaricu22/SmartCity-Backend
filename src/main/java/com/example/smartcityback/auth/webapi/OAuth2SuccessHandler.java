package com.example.smartcityback.auth.webapi;

import com.example.smartcityback.auth.infrastructure.InMemoryUserRegistry;
import com.example.smartcityback.auth.infrastructure.JwtTokenService;
import com.example.smartcityback.auth.infrastructure.RefreshTokenStore;
import com.example.smartcityback.auth.infrastructure.RegisteredUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/*
 * After Google redirects back and Spring validates the OAuth2 token, this handler:
 *   1. Extracts the email from the Google OAuth2User principal
 *   2. Looks up or creates the user in InMemoryUserRegistry (idempotent)
 *   3. Issues our own JWT + refresh token — same format as POST /v1/auth/login
 *   4. Redirects the browser to the Angular app with credentials in the fragment (#)
 *
 * Fragment (#) is used instead of query param (?): the browser never sends fragment
 * values to the server, so the token does not appear in server access logs or
 * Referer headers on subsequent navigation.
 *
 * SimpleUrlAuthenticationSuccessHandler is extended (rather than implementing
 * AuthenticationSuccessHandler directly) to inherit getRedirectStrategy(), which
 * handles both relative and absolute redirect URLs correctly across all environments.
 */
@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final InMemoryUserRegistry registry;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenStore refreshTokenStore;
    private final long expirationMs;
    private final String frontendCallbackUrl;

    public OAuth2SuccessHandler(
            InMemoryUserRegistry registry,
            JwtTokenService jwtTokenService,
            RefreshTokenStore refreshTokenStore,
            @Value("${app.jwt.expiration-ms}") long expirationMs,
            @Value("${app.oauth2.frontend-callback-url}") String frontendCallbackUrl) {
        this.registry = registry;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenStore = refreshTokenStore;
        this.expirationMs = expirationMs;
        this.frontendCallbackUrl = frontendCallbackUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        RegisteredUser user = registry.findOrRegisterOAuth(email);
        String token = jwtTokenService.generate(email, user.role());
        String refreshToken = refreshTokenStore.issue(email, user.role());
        String redirectUrl = frontendCallbackUrl
                + "#token=" + token
                + "&role=" + user.role()
                + "&expiresInMs=" + expirationMs
                + "&refreshToken=" + refreshToken;
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
