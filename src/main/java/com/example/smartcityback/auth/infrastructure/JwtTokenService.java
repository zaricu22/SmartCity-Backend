package com.example.smartcityback.auth.infrastructure;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtTokenService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtTokenService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        // The secret env var is BASE64-encoded raw bytes, not a plain string.
        // HMAC keys are binary material; BASE64 is the standard way to store them in
        // an env var without charset or encoding ambiguity. Keys.hmacShaKeyFor() also
        // enforces a minimum of 256 bits (32 bytes) — the JWT spec minimum for HS256.
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expirationMs = expirationMs;
    }

    public String generate(String username, String role) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())  // jti — unique ID required for revocation
                .subject(username)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    public Claims validate(String token) throws JwtException {
        // parseSignedClaims throws JwtException for any failure: expired, bad signature,
        // malformed, or wrong key. JwtAuthFilter catches all of these as one case and
        // leaves the SecurityContext empty, which the framework resolves as 401.
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
