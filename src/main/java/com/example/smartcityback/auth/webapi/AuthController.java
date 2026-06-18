package com.example.smartcityback.auth.webapi;

import com.example.smartcityback.auth.infrastructure.JwtAuthDetails;
import com.example.smartcityback.auth.infrastructure.JwtTokenService;
import com.example.smartcityback.auth.infrastructure.RefreshTokenStore;
import com.example.smartcityback.auth.infrastructure.TokenBlacklist;
import com.example.smartcityback.auth.webapi.request.LoginRequest;
import com.example.smartcityback.auth.webapi.request.LogoutRequest;
import com.example.smartcityback.auth.webapi.request.RefreshRequest;
import com.example.smartcityback.auth.webapi.response.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthenticationManager authManager;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenStore refreshTokenStore;
    private final TokenBlacklist tokenBlacklist;
    private final long expirationMs;

    public AuthController(AuthenticationManager authManager,
                          JwtTokenService jwtTokenService,
                          RefreshTokenStore refreshTokenStore,
                          TokenBlacklist tokenBlacklist,
                          @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.authManager = authManager;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenStore = refreshTokenStore;
        this.tokenBlacklist = tokenBlacklist;
        this.expirationMs = expirationMs;
    }

    @Operation(summary = "Authenticate and receive a JWT + refresh token")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            Authentication auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
            String role = extractRole(auth);
            String token = jwtTokenService.generate(auth.getName(), role);
            String refreshToken = refreshTokenStore.issue(auth.getName(), role);
            return ResponseEntity.ok(new LoginResponse(token, role, expirationMs, refreshToken));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @Operation(summary = "Exchange a refresh token for a new JWT + new refresh token")
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return refreshTokenStore.consume(request.refreshToken())
                .map(entry -> {
                    String newToken = jwtTokenService.generate(entry.username(), entry.role());
                    String newRefreshToken = refreshTokenStore.issue(entry.username(), entry.role());
                    return ResponseEntity.ok(new LoginResponse(newToken, entry.role(), expirationMs, newRefreshToken));
                })
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Operation(summary = "Revoke the current JWT and its refresh token")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // Safe cast: anyRequest().authenticated() guarantees a valid JWT was processed by
        // JwtAuthFilter before this method is reached, and JwtAuthFilter always sets JwtAuthDetails.
        JwtAuthDetails details = (JwtAuthDetails) auth.getDetails();
        tokenBlacklist.revoke(details.jti(), details.expiresAt());
        refreshTokenStore.consume(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    private String extractRole(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .findFirst()
                .orElse("VIEWER");
    }
}
