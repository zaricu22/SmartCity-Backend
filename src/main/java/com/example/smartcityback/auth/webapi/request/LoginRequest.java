package com.example.smartcityback.auth.webapi.request;

import jakarta.validation.constraints.NotBlank;

/*
 * 'username' accepts either a plain username (pre-seeded admin/viewer accounts) or an
 * email address (self-registered users). InMemoryUserRegistry.loadUserByUsername looks
 * up whatever string was used as the map key at registration time — there is no distinction
 * in the lookup, so a single field covers both cases without a separate loginType param.
 */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password
) {}
