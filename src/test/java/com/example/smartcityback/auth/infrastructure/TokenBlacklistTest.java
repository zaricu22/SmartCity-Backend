package com.example.smartcityback.auth.infrastructure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBlacklistTest {

    private final TokenBlacklist blacklist = new TokenBlacklist();

    @Test
    void revoke_thenIsRevoked_returnsTrue() {
        blacklist.revoke("jti-1", System.currentTimeMillis() + 60_000);
        assertThat(blacklist.isRevoked("jti-1")).isTrue();
    }

    @Test
    void unknownJti_isRevoked_returnsFalse() {
        assertThat(blacklist.isRevoked("unknown-jti")).isFalse();
    }

    @Test
    void evictExpired_removesExpiredEntries_butKeepsActive() {
        blacklist.revoke("expired", System.currentTimeMillis() - 1);
        blacklist.revoke("active", System.currentTimeMillis() + 60_000);

        blacklist.evictExpired();

        assertThat(blacklist.isRevoked("expired")).isFalse();
        assertThat(blacklist.isRevoked("active")).isTrue();
    }
}
