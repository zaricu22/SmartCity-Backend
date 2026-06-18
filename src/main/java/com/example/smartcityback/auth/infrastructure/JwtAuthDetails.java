package com.example.smartcityback.auth.infrastructure;

/*
 * Carried on UsernamePasswordAuthenticationToken.details after JWT validation.
 * Allows downstream components (e.g. logout endpoint) to access the JTI and expiry
 * without re-parsing the Authorization header a second time.
 */
public record JwtAuthDetails(String jti, long expiresAt) {}
