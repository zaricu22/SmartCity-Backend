package com.example.smartcityback.auth.infrastructure;

/*
 * REFRESH TOKEN FLOW — Design decisions
 *
 * POST /v1/auth/login
 *   <- { accessToken (JWT, short-lived), refreshToken (UUID, long-lived) }
 *
 * POST /v1/auth/refresh
 *   -> { refreshToken: "uuid" }
 *   <- { accessToken (new JWT), refreshToken (new UUID) }   — rotating tokens
 *
 * Storage: ConcurrentHashMap<uuid, Entry{username, role, expiresAt}>
 *   - In-memory only — tokens are lost on restart (acceptable for dev/educational use)
 *   - For production: persist in DB or Redis so tokens survive restarts and scale horizontally
 *
 * Single-use: store.remove(token) atomically consumes and invalidates the token.
 *   - Prevents replay attacks — a stolen refresh token can only be used once
 *   - Rotating: every refresh issues a fresh refreshToken, old one is gone
 *
 * Expiry: checked after removal — expired entries are silently dropped (no re-insertion).
 *   - No background cleanup needed at dev scale; for production add a scheduled eviction job
 */

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RefreshTokenStore {

    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    private final long expiryMs;

    public RefreshTokenStore(@Value("${app.jwt.refresh-expiration-ms}") long expiryMs) {
        this.expiryMs = expiryMs;
    }

    public String issue(String username, String role) {
        String token = UUID.randomUUID().toString();
        store.put(token, new Entry(username, role, System.currentTimeMillis() + expiryMs));
        return token;
    }

    // Atomic remove — token is invalidated whether valid or expired, preventing replay
    public Optional<Entry> consume(String token) {
        Entry entry = store.remove(token);
        if (entry == null || entry.isExpired()) return Optional.empty();
        return Optional.of(entry);
    }

    public record Entry(String username, String role, long expiresAt) {
        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
