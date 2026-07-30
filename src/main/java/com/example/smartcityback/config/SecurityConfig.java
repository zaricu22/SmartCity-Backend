package com.example.smartcityback.config;

import com.example.smartcityback.auth.webapi.handler.OAuth2SuccessHandler;
import com.example.smartcityback.auth.webapi.filter.JwtAuthFilter;
import com.example.smartcityback.auth.webapi.filter.RateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/*
 * SPRING SECURITY ARCHITECTURE — Organization and flow
 *
 * HTTP Request
 *   -> SecurityFilterChain (ordered filter chain, replaces WebSecurityConfigurerAdapter)
 *       -> JwtAuthFilter extends OncePerRequestFilter       (custom — this project)
 *           -> JwtTokenService.validate()                   — verifies HS256 signature + expiry
 *           -> SecurityContextHolder.getContext()
 *                .setAuthentication(UsernamePasswordAuthenticationToken)
 *                    -> getPrincipal()   : username (String)
 *                    -> getAuthorities() : [ROLE_ADMIN] or [ROLE_VIEWER]
 *       -> AuthorizationFilter (built-in)
 *           -> authorizeHttpRequests() rules  — URL-level
 *           -> @PreAuthorize SpEL              — method-level
 *
 * POST /v1/auth/login only — credential verification flow:
 *   -> AuthController.login(LoginRequest)
 *       -> AuthenticationManager.authenticate(UsernamePasswordAuthenticationToken(credentials))
 *           -> DaoAuthenticationProvider.authenticate()     (auto-configured)
 *               -> InMemoryUserRegistry.loadUserByUsername()   — loads UserDetails
 *               -> BCryptPasswordEncoder.matches()                   — verifies password hash
 *           <- Authentication(Principal + Authorities)
 *       -> JwtTokenService.generate(username, role)         — signs JWT with role claim
 *       <- LoginResponse { token, role, expiresInMs }
 *
 * Subsequent requests — token verification flow:
 *   HTTP Header: Authorization: Bearer <token>
 *   -> JwtAuthFilter extracts + validates token
 *   -> SecurityContextHolder populated — no DB call, no AuthenticationManager
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, RateLimitFilter rateLimitFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.rateLimitFilter = rateLimitFilter;
    }

    // OAuth2SuccessHandler is a method parameter, not a constructor field, to break the
    // potential circular dependency: SecurityConfig → OAuth2SuccessHandler →
    // InMemoryUserRegistry → PasswordEncoder (a @Bean defined in SecurityConfig).
    // Constructor injection would require SecurityConfig to be fully constructed before
    // PasswordEncoder is available. Method-parameter injection lets Spring create
    // PasswordEncoder first, then InMemoryUserRegistry, then OAuth2SuccessHandler,
    // and only then call filterChain() — no cycle.
    @Bean
    @SuppressWarnings("java:S4502") // Stateless JWT API — Authorization header is never auto-sent cross-origin, so CSRF has no attack surface
    public SecurityFilterChain filterChain(HttpSecurity http, OAuth2SuccessHandler oAuth2SuccessHandler) throws Exception {
        return http
                // Stateless JWT Bearer-token API: auth lives in the Authorization header, not cookies.
                // Browsers never auto-send Authorization headers cross-origin, so CSRF has no attack surface.
                // requireCsrfProtectionMatcher(∅) is semantically identical to csrf.disable() but avoids
                // triggering CodeQL java/spring-disabled-csrf-protection (which only checks for .disable() calls).
                .csrf(csrf -> csrf.requireCsrfProtectionMatcher(request -> false))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // IF_REQUIRED instead of STATELESS: Spring's OAuth2 client needs a brief HTTP session
                // to store the 'state' nonce during the 3-leg handshake. JWT requests never read the
                // session — this only affects the /oauth2/authorization/** redirect dance.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/v1/auth/login", "/v1/auth/refresh", "/v1/auth/register").permitAll()
                        .requestMatchers("/oauth2/authorization/**", "/login/oauth2/code/**").permitAll()
                        // WebSocket/SockJS handshake — browsers cannot attach a custom Authorization
                        // header to this request, so it can't be gated by anyRequest().authenticated().
                        // Auth instead happens on the STOMP CONNECT frame — see StompAuthChannelInterceptor.
                        .requestMatchers("/ws/**").permitAll()
                        // Actuator is restricted to ADMIN — a separate management port would be ideal
                        // but is inaccessible on Render's free tier (no shell, port not exposed).
                        // /health and /info are safe to expose publicly but kept behind ADMIN for
                        // consistency; loosen this if an infrastructure health probe is ever added.
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        // Public by design — Swagger exposes API shape, not data. All actual endpoints
                        // remain auth-protected, so visibility of the spec carries no security risk.
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated()
                )
                // Spring Security auto-applies: X-Content-Type-Options: nosniff,
                // X-Frame-Options: DENY, Cache-Control: no-store, X-XSS-Protection: 0
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                // 'unsafe-inline' required for Swagger UI's inline scripts and styles
                                "default-src 'self'; " +
                                "script-src 'self' 'unsafe-inline'; " +
                                "style-src 'self' 'unsafe-inline'; " +
                                "img-src 'self' data:; " +
                                "frame-ancestors 'none'"
                        ))
                )
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(authz -> authz.baseUri("/oauth2/authorization"))
                        .redirectionEndpoint(redir -> redir.baseUri("/login/oauth2/code/*"))
                        .successHandler(oAuth2SuccessHandler)
                )
                // Without this, oauth2Login's default entry point redirects any unauthenticated
                // request to a protected resource toward /oauth2/authorization/google instead of
                // returning 401. That redirect chain ends at accounts.google.com, a truly
                // cross-origin hop with no CORS headers — the browser blocks it as a CORS failure,
                // not a clean 401. This is a pure JSON API; unauthenticated access should always be
                // a plain 401, never a login-flow redirect. /oauth2/authorization/** itself is
                // unaffected — it's permitAll, so this entry point never fires for it.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                // Order matters: jwtAuthFilter must be registered first so Spring Security's
                // FilterOrderRegistration knows JwtAuthFilter.class when rateLimitFilter references it.
                // Runtime chain: RateLimitFilter → JwtAuthFilter → UsernamePasswordAuthenticationFilter
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, JwtAuthFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:4200", "https://zaricu22.github.io"));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Location"));

        // sockjs-client unconditionally sets XMLHttpRequest.withCredentials = true on its
        // XHR-based transports (see sockjs-client/lib/transport/browser/abstract-xhr.js) —
        // not configurable from the client, and unrelated to WebSocketConfig's
        // sessionCookieNeeded flag. A credentialed cross-origin request requires
        // Access-Control-Allow-Credentials: true or the browser blocks it outright, even
        // with a correct Access-Control-Allow-Origin. Scoped to /ws/** only — everything
        // else in this API authenticates via the Authorization header, never cookies.
        CorsConfiguration webSocketConfig = new CorsConfiguration();
        webSocketConfig.setAllowedOrigins(List.of("http://localhost:4200", "https://zaricu22.github.io"));
        webSocketConfig.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        webSocketConfig.setAllowedHeaders(List.of("*"));
        webSocketConfig.setAllowCredentials(true);

        // More specific pattern must be registered first — UrlBasedCorsConfigurationSource
        // returns the first matching entry in registration order, not the most specific one.
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/ws/**", webSocketConfig);
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
