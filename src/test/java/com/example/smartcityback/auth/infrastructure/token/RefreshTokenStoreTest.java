package com.example.smartcityback.auth.infrastructure.token;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenStoreTest {

    private final RefreshTokenStore store = new RefreshTokenStore(3_600_000);

    @Test
    @DisplayName("returns the username and role that were issued when a freshly issued refresh token is consumed")
    void issue_thenConsume_returnsEntry() {
        String token = store.issue("admin", "ADMIN");
        var result = store.consume(token);
        assertThat(result).isPresent();
        assertThat(result.get().username()).isEqualTo("admin");
        assertThat(result.get().role()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("returns empty for a refresh token that was never issued")
    void consume_unknownToken_returnsEmpty() {
        assertThat(store.consume("does-not-exist")).isEmpty();
    }

    @Test
    @DisplayName("returns empty for a refresh token issued with a negative TTL, so it's already expired the "
            + "instant it's created — no waiting or clock mocking needed")
    void consume_expiredToken_returnsEmpty() {
        RefreshTokenStore expiredStore = new RefreshTokenStore(-1);
        String token = expiredStore.issue("admin", "ADMIN");
        assertThat(expiredStore.consume(token)).isEmpty();
    }

    @Test
    @DisplayName("returns empty on a second consume of the same refresh token, proving a token can only be "
            + "redeemed once")
    void consume_singleUse_secondCallReturnsEmpty() {
        String token = store.issue("admin", "ADMIN");
        store.consume(token);
        assertThat(store.consume(token)).isEmpty();
    }
}
