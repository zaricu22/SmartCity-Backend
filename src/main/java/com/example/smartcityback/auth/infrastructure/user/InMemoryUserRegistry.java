package com.example.smartcityback.auth.infrastructure.user;

/*
 * USER REGISTRY — replaces InMemoryUserDetailsManager
 *
 * Two kinds of users share one ConcurrentHashMap<email-or-username, RegisteredUser>:
 *   LOCAL  — registered via POST /v1/auth/register (email + BCrypt password)
 *   GOOGLE — auto-created on first OAuth2 login (email only, no password)
 *
 * Pre-seeded hardcoded users (admin, viewer) use their username as the map key,
 * matching the username field in LoginRequest — no migration needed.
 *
 * Email-as-key means a user who registered locally and later logs in via Google
 * with the same address is recognised as the same account (findOrRegisterOAuth
 * hits the existing entry and returns it without overwriting).
 */

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryUserRegistry implements UserDetailsService {

    private final Map<String, RegisteredUser> store = new ConcurrentHashMap<>();
    private final PasswordEncoder encoder;

    public InMemoryUserRegistry(
            PasswordEncoder encoder,
            @Value("${app.security.admin.password}") String adminPwd,
            @Value("${app.security.viewer.password}") String viewerPwd) {
        this.encoder = encoder;
        store.put("admin", new RegisteredUser("admin", encoder.encode(adminPwd), "ADMIN", RegisteredUser.AuthProvider.LOCAL));
        store.put("viewer", new RegisteredUser("viewer", encoder.encode(viewerPwd), "VIEWER", RegisteredUser.AuthProvider.LOCAL));
    }

    @Override
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        RegisteredUser user = store.get(usernameOrEmail);
        if (user == null) throw new UsernameNotFoundException(usernameOrEmail);
        // GOOGLE users have no password. The empty string ensures DaoAuthenticationProvider
        // rejects any login attempt via POST /v1/auth/login, because BCrypt never produces
        // a hash that matches "". They must authenticate via /oauth2/authorization/google.
        return User.builder()
                .username(user.email())
                .password(user.hashedPassword() != null ? user.hashedPassword() : "")
                .authorities(new SimpleGrantedAuthority("ROLE_" + user.role()))
                .build();
    }

    public void register(String email, String rawPassword) {
        // BCrypt first, then atomic putIfAbsent — avoids the TOCTOU gap between a
        // containsKey check and a subsequent put when two requests race on the same email.
        RegisteredUser candidate = new RegisteredUser(email, encoder.encode(rawPassword), "VIEWER", RegisteredUser.AuthProvider.LOCAL);
        if (store.putIfAbsent(email, candidate) != null) {
            throw new EmailAlreadyRegisteredException(email);
        }
    }

    public RegisteredUser findOrRegisterOAuth(String email) {
        // computeIfAbsent is atomic: two concurrent first-logins from the same Google
        // account produce exactly one registry entry.
        return store.computeIfAbsent(email,
                k -> new RegisteredUser(email, null, "VIEWER", RegisteredUser.AuthProvider.GOOGLE));
    }
}
