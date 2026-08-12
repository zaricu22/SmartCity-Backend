package com.example.smartcityback.auth.infrastructure.token;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBlacklistTest {

    private final TokenBlacklist blacklist = new TokenBlacklist();

    @Test
    @DisplayName("reports a token's JTI as revoked immediately after it's been revoked")
    void revoke_thenIsRevoked_returnsTrue() {
        blacklist.revoke("jti-1", System.currentTimeMillis() + 60_000);
        assertThat(blacklist.isRevoked("jti-1")).isTrue();
    }

    @Test
    @DisplayName("reports a JTI that was never revoked as not revoked")
    void unknownJti_isRevoked_returnsFalse() {
        assertThat(blacklist.isRevoked("unknown-jti")).isFalse();
    }

    @Test
    @DisplayName("removes an expired blacklist entry on eviction while leaving a still-active one revoked")
    void evictExpired_removesExpiredEntries_butKeepsActive() {
        blacklist.revoke("expired", System.currentTimeMillis() - 1);
        blacklist.revoke("active", System.currentTimeMillis() + 60_000);

        blacklist.evictExpired();

        assertThat(blacklist.isRevoked("expired")).isFalse();
        assertThat(blacklist.isRevoked("active")).isTrue();
    }
}
