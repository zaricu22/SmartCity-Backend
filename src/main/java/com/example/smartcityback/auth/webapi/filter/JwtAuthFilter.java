package com.example.smartcityback.auth.webapi.filter;

import com.example.smartcityback.auth.infrastructure.JwtAuthDetails;
import com.example.smartcityback.auth.infrastructure.JwtTokenService;
import com.example.smartcityback.auth.infrastructure.TokenBlacklist;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final TokenBlacklist tokenBlacklist;

    public JwtAuthFilter(JwtTokenService jwtTokenService, TokenBlacklist tokenBlacklist) {
        this.jwtTokenService = jwtTokenService;
        this.tokenBlacklist = tokenBlacklist;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length());
            try {
                Claims claims = jwtTokenService.validate(token);

                // Reject tokens that were explicitly revoked via POST /v1/auth/logout
                if (!tokenBlacklist.isRevoked(claims.getId())) {
                    String role = claims.get("role", String.class);
                    var auth = new UsernamePasswordAuthenticationToken(
                            claims.getSubject(),
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role))
                    );
                    // Attach jti + expiry so the logout endpoint can revoke without re-parsing the header
                    auth.setDetails(new JwtAuthDetails(claims.getId(), claims.getExpiration().getTime()));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (JwtException ignored) {
                // Invalid or expired token — security context stays empty; framework returns 401
            }
        }
        chain.doFilter(request, response);
    }
}
