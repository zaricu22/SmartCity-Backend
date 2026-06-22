package com.example.smartcityback.auth.infrastructure.token;

/*
 * ACCESS TOKEN REVOCATION — Design decisions
 *
 * Problem: JWTs are stateless — once issued they are valid until expiry.
 *   Logout must also kill the access token, not just the refresh token.
 *
 * Solution: in-memory blacklist of revoked JTIs (JWT IDs).
 *   - Every token carries a unique 'jti' claim (UUID) added at generation time.
 *   - JwtAuthFilter checks the blacklist after signature validation.
 *     A revoked JTI is treated as if no token was provided (401).
 *
 * Storage: ConcurrentHashMap<jti, expiresAt (epoch ms)>
 *   - We only need to hold a JTI until the token would have expired anyway —
 *     after that it is rejected by signature validation regardless.
 *   - @Scheduled eviction runs hourly to prevent unbounded memory growth.
 *
 * Production note: replace with Redis (Lettuce/Redisson).
 *   Each app instance has its own map — revocation on one node is invisible
 *   to others. Redis gives a single shared blacklist across the cluster.
 */

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenBlacklist {

    private final Map<String, Long> revokedJtis = new ConcurrentHashMap<>();

    public void revoke(String jti, long expiresAt) {
        revokedJtis.put(jti, expiresAt);
    }

    public boolean isRevoked(String jti) {
        return revokedJtis.containsKey(jti);
    }

    // Entries past their expiry are meaningless — JWT validation rejects them anyway
    @Scheduled(fixedRate = 3_600_000)
    public void evictExpired() {
        long now = System.currentTimeMillis();
        revokedJtis.entrySet().removeIf(e -> e.getValue() < now);
    }
}
