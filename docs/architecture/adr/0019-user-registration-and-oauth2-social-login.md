# ADR-0019: User Registration, OAuth2 Social Login, and In-Memory Identity Store

**Status:** Accepted  
**Date:** 2026-06-20

## Context

The system previously had two hardcoded users (`admin`, `viewer`) stored in Spring's
`InMemoryUserDetailsManager` and seeded from environment variables. No registration
flow existed — user accounts could only be created by changing application properties
and restarting the server.

Two requirements were added:

1. **Self-registration** — users should be able to create an account with email and password.
2. **Google social login** — users should be able to authenticate via Google OAuth2
   without managing a separate password.

## Decision

### 1. Email is the identity key

Email is used as the primary key in `InMemoryUserRegistry`. A user who registers via
email/password and later signs in via Google with the same address is recognised as
the same account — `findOrRegisterOAuth` performs a `computeIfAbsent`, which returns
the existing entry without overwriting it.

Alternative considered: treat LOCAL and GOOGLE accounts as separate identities keyed
by `provider:email`. Rejected because it would create duplicate accounts for the same
person and require a manual merge step.

### 2. InMemoryUserRegistry replaces InMemoryUserDetailsManager

`InMemoryUserDetailsManager` is a fixed list — it has no mutation API suitable for
registration. `InMemoryUserRegistry` wraps a `ConcurrentHashMap` and implements
`UserDetailsService`, so Spring's `DaoAuthenticationProvider` continues to work
unchanged for the existing `POST /v1/auth/login` path. Pre-seeded admin and viewer
accounts are written into the map at startup.

This is explicitly an in-memory, non-persistent store. All registered accounts are
lost on restart. The design is intentional for the current educational/dev phase.
Migration path: implement `UserDetailsService` backed by a JPA `UserEntity` and
remove `InMemoryUserRegistry`.

### 3. Auto-login on registration (201 + token body)

`POST /v1/auth/register` returns `201 Created` with a `LoginResponse` body (JWT +
refresh token). The alternative — returning `201` with no body and requiring a
subsequent `POST /v1/auth/login` — was rejected as an unnecessary extra round-trip.
The credentials are trivially valid immediately after registration; re-authenticating
them through `AuthenticationManager` would be redundant work.

### 4. All new users receive VIEWER role

Self-registered users and Google OAuth2 users both receive `ROLE_VIEWER` automatically.
Admin accounts are only available through the hardcoded `admin` user seeded from the
`ADMIN_PASSWORD` environment variable. There is no self-service path to ADMIN.

This keeps privilege escalation out of the registration flow without requiring an admin
approval workflow that would add complexity with no educational benefit at this stage.

### 5. GOOGLE accounts cannot log in via password

`RegisteredUser.hashedPassword` is `null` for GOOGLE-only accounts.
`InMemoryUserRegistry.loadUserByUsername` substitutes an empty string `""` for null.
`BCryptPasswordEncoder.matches(rawPassword, "")` always returns false, so any attempt
to log in via `POST /v1/auth/login` with a GOOGLE-only email is silently rejected with
`401 Unauthorized` — no special code path is needed.

### 6. OAuth2 token delivery via URL fragment

After `OAuth2SuccessHandler` issues a JWT, it redirects the browser to the Angular
app at `app.oauth2.frontend-callback-url` with credentials appended as a URL fragment:

```
https://<frontend>/callback#token=...&role=...&expiresInMs=...&refreshToken=...
```

Fragment (`#`) is used instead of query string (`?`) because browsers never send
fragment values to the server. This means the token does not appear in:
- Backend access logs (the redirect target URL is logged, but not the fragment)
- `Referer` headers of subsequent navigation requests

Alternative considered: POST the token as a JSON body to a dedicated frontend endpoint.
Rejected because the Angular app is a static SPA with no server to receive POST requests.

### 7. Registration endpoint is rate-limited

`POST /v1/auth/register` is included in `RateLimitFilter.RATE_LIMITED_PATHS` for two
reasons:
- The `409 Conflict` response leaks whether an email is already registered. Without
  rate limiting, an attacker can enumerate registered emails cheaply.
- BCrypt hashing takes ~100 ms per call. Without rate limiting, a flood of registration
  requests is an inexpensive CPU exhaustion vector.

## Consequences

**Positive:**
- Users can self-register and immediately use the application without admin intervention
- Google social login eliminates password management for users who prefer it
- Both auth paths produce identical JWT tokens — downstream code (`JwtAuthFilter`,
  `@PreAuthorize`) is completely unaffected
- Email-as-identity-key provides natural account merging across providers with no
  explicit merge UI required

**Negative:**
- All registered accounts are lost on restart — this is acceptable for dev/educational
  use but must be replaced with a DB-backed store before production
- Email enumeration via `409 Conflict` is mitigated by rate limiting but not eliminated
  (a slow enumeration within the rate limit window is still possible)
- Password reset and email verification are not implemented — GOOGLE users who want
  to set a LOCAL password have no self-service path to do so
- The `state` nonce during the OAuth2 handshake requires a brief HTTP session, which
  required relaxing `SessionCreationPolicy` from `STATELESS` to `IF_REQUIRED`
  (see ADR-0017 amendment)
