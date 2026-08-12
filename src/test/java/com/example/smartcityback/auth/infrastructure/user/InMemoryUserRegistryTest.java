package com.example.smartcityback.auth.infrastructure.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class InMemoryUserRegistryTest {

    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private InMemoryUserRegistry registry;

    @BeforeEach
    void setUp() {
        given(encoder.encode(any())).willAnswer(inv -> "hashed:" + inv.getArgument(0));
        registry = new InMemoryUserRegistry(encoder, "admin123", "viewer123");
    }

    // -------------------------------------------------------------------------
    // loadUserByUsername
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("loads the pre-seeded admin account with the ADMIN role")
    void loadUserByUsername_seededAdmin_returnsAdminRole() {
        UserDetails details = registry.loadUserByUsername("admin");
        assertThat(details.getUsername()).isEqualTo("admin");
        assertThat(details.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("loads the pre-seeded viewer account with the VIEWER role")
    void loadUserByUsername_seededViewer_returnsViewerRole() {
        UserDetails details = registry.loadUserByUsername("viewer");
        assertThat(details.getAuthorities()).extracting("authority").containsExactly("ROLE_VIEWER");
    }

    @Test
    @DisplayName("rejects a username that was never registered")
    void loadUserByUsername_unknownUser_throwsUsernameNotFoundException() {
        assertThatThrownBy(() -> registry.loadUserByUsername("nobody@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    @DisplayName("returns an empty-string password for a Google-registered user, so Spring Security's "
            + "password check can never match any raw password against it")
    void loadUserByUsername_googleUser_returnsEmptyPassword() {
        registry.findOrRegisterOAuth("google@example.com");
        UserDetails details = registry.loadUserByUsername("google@example.com");
        // Empty string prevents DaoAuthenticationProvider from matching any raw password
        assertThat(details.getPassword()).isEqualTo("");
    }

    // -------------------------------------------------------------------------
    // register
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("makes a newly registered email immediately loadable, with the VIEWER role assigned by default")
    void register_newEmail_userIsLoadableAfterwards() {
        registry.register("new@example.com", "password123");
        UserDetails details = registry.loadUserByUsername("new@example.com");
        assertThat(details.getUsername()).isEqualTo("new@example.com");
        assertThat(details.getAuthorities()).extracting("authority").containsExactly("ROLE_VIEWER");
    }

    @Test
    @DisplayName("stores a newly registered user's password hashed, never in plain text")
    void register_newEmail_passwordIsHashed() {
        registry.register("new@example.com", "password123");
        UserDetails details = registry.loadUserByUsername("new@example.com");
        assertThat(details.getPassword()).isEqualTo("hashed:password123");
    }

    @Test
    @DisplayName("rejects registering an email that's already taken")
    void register_duplicateEmail_throwsEmailAlreadyRegisteredException() {
        registry.register("dup@example.com", "password123");
        assertThatThrownBy(() -> registry.register("dup@example.com", "other"))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    // -------------------------------------------------------------------------
    // findOrRegisterOAuth
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("creates a new Google-provider user with the VIEWER role and no password hash on first "
            + "OAuth login")
    void findOrRegisterOAuth_newEmail_createsGoogleUserWithViewerRole() {
        RegisteredUser user = registry.findOrRegisterOAuth("google@example.com");
        assertThat(user.email()).isEqualTo("google@example.com");
        assertThat(user.role()).isEqualTo("VIEWER");
        assertThat(user.provider()).isEqualTo(RegisteredUser.AuthProvider.GOOGLE);
        assertThat(user.hashedPassword()).isNull();
    }

    @Test
    @DisplayName("keeps an existing password-based account as LOCAL when its email logs in through Google, "
            + "instead of silently overwriting it with a passwordless GOOGLE entry")
    void findOrRegisterOAuth_existingLocalEmail_returnsExistingUserWithoutOverwriting() {
        registry.register("local@example.com", "password123");
        RegisteredUser user = registry.findOrRegisterOAuth("local@example.com");
        // Must return the existing LOCAL account, not replace it with a GOOGLE entry
        assertThat(user.provider()).isEqualTo(RegisteredUser.AuthProvider.LOCAL);
        assertThat(user.hashedPassword()).isNotNull();
    }

    @Test
    @DisplayName("returns the same user on a second OAuth login for the same email, instead of creating a duplicate")
    void findOrRegisterOAuth_calledTwice_returnsSameUser() {
        RegisteredUser first = registry.findOrRegisterOAuth("google@example.com");
        RegisteredUser second = registry.findOrRegisterOAuth("google@example.com");
        assertThat(first).isEqualTo(second);
    }
}
