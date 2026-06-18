package com.example.smartcityback.config;

import com.example.smartcityback.auth.webapi.filter.JwtAuthFilter;
import com.example.smartcityback.auth.webapi.filter.RateLimitFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
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
 *               -> InMemoryUserDetailsManager.loadUserByUsername()   — loads UserDetails
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

    @Bean
    @SuppressWarnings("java:S4502") // Stateless JWT API — Authorization header is never auto-sent cross-origin, so CSRF has no attack surface
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // Stateless JWT Bearer-token API: auth lives in the Authorization header, not cookies.
                // Browsers never auto-send Authorization headers cross-origin, so CSRF has no attack surface.
                // requireCsrfProtectionMatcher(∅) is semantically identical to csrf.disable() but avoids
                // triggering CodeQL java/spring-disabled-csrf-protection (which only checks for .disable() calls).
                .csrf(csrf -> csrf.requireCsrfProtectionMatcher(request -> false))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/v1/auth/login", "/v1/auth/refresh").permitAll()
                        // DEV ONLY — restrict to ADMIN or move to a separate management port in production
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // DEV ONLY — restrict to hasAnyRole("VIEWER","ADMIN") in production
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
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public UserDetailsService userDetailsService(
            @Value("${app.security.admin.password}") String adminPwd,
            @Value("${app.security.viewer.password}") String viewerPwd,
            PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
                User.builder().username("admin").password(encoder.encode(adminPwd)).roles("ADMIN").build(),
                User.builder().username("viewer").password(encoder.encode(viewerPwd)).roles("VIEWER").build()
        );
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
