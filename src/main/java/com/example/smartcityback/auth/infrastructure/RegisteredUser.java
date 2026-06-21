package com.example.smartcityback.auth.infrastructure;

/*
 * Identity record shared by all authentication paths.
 *
 * hashedPassword is null for GOOGLE accounts — those users have no password and can
 * only authenticate via OAuth2. InMemoryUserRegistry.loadUserByUsername substitutes
 * an empty string so DaoAuthenticationProvider rejects password-login attempts without
 * a special code path.
 *
 * email is the identity anchor across providers: if a user registers with LOCAL and
 * later logs in via Google using the same address, InMemoryUserRegistry.findOrRegisterOAuth
 * finds the existing entry and returns it, so both paths share one account.
 */
public record RegisteredUser(String email, String hashedPassword, String role, AuthProvider provider) {

    public enum AuthProvider {
        LOCAL, GOOGLE
    }
}
