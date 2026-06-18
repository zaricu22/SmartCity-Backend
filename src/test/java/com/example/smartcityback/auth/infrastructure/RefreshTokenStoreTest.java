package com.example.smartcityback.auth.infrastructure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenStoreTest {

    private final RefreshTokenStore store = new RefreshTokenStore(3_600_000);

    @Test
    void issue_thenConsume_returnsEntry() {
        String token = store.issue("admin", "ADMIN");
        var result = store.consume(token);
        assertThat(result).isPresent();
        assertThat(result.get().username()).isEqualTo("admin");
        assertThat(result.get().role()).isEqualTo("ADMIN");
    }

    @Test
    void consume_unknownToken_returnsEmpty() {
        assertThat(store.consume("does-not-exist")).isEmpty();
    }

    @Test
    void consume_expiredToken_returnsEmpty() {
        RefreshTokenStore expiredStore = new RefreshTokenStore(-1);
        String token = expiredStore.issue("admin", "ADMIN");
        assertThat(expiredStore.consume(token)).isEmpty();
    }

    @Test
    void consume_singleUse_secondCallReturnsEmpty() {
        String token = store.issue("admin", "ADMIN");
        store.consume(token);
        assertThat(store.consume(token)).isEmpty();
    }
}
