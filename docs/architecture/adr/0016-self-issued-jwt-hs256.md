# ADR-0016: Self-Issued JWT with HS256 over OAuth2/OIDC Provider

**Status:** Accepted  
**Date:** 2026-06-17

## Context

The REST API needed stateless authentication. Three approaches were considered:

**Option A — OAuth2/OIDC provider (Keycloak, Auth0, AWS Cognito)**  
An external identity provider issues tokens; the app acts as a Resource Server and
validates them against the provider's public JWKS endpoint.

**Option B — Self-issued JWT, RS256 (asymmetric)**  
The app generates its own tokens, signed with a private RSA key. Any service that has
the public key can verify tokens without contacting the issuer. Standard for
multi-service architectures.

**Option C — Self-issued JWT, HS256 (symmetric)**  
The app generates and verifies tokens using the same HMAC-SHA256 secret key.
Simpler than RS256 but requires every verifying party to share the secret.

## Decision

Use **self-issued JWT with HS256** (Option C).

Reasons for rejecting Option A:

- An external OIDC provider is a runtime dependency. Running Keycloak requires a
  separate Docker container; Auth0/Cognito require an account and network access.
  This breaks the project goal of being fully self-contained and runnable with a
  single `mvn spring-boot:run`.
- Switching to an OIDC provider later is a configuration change, not a code change.
  Spring Security's `oauth2ResourceServer().jwt()` works against any provider by
  pointing `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` at the provider's
  JWKS endpoint. The `JwtAuthFilter` would be replaced by one line of config.

Reasons for choosing HS256 over RS256:

- RS256 is the correct choice when multiple independent services verify the same
  token without sharing a secret. This project is a **single-service monolith** —
  only one process both issues and verifies tokens. There are no other services that
  need to read the JWT, so the asymmetric key pair adds complexity with no benefit.
- HS256 with a 256-bit secret is cryptographically sound for a single-service setup.
- Key rotation: both algorithms require manual key rotation in this self-issued setup.
  RS256 does not provide an advantage here without a JWKS endpoint infrastructure.

## Consequences

**Positive:**
- Zero external dependencies — the app starts without any auth infrastructure
- Full control over token claims (jti, role, expiry) without provider configuration
- Educational clarity: token generation and validation are explicit in `JwtTokenService`
  rather than hidden behind OAuth2 auto-configuration

**Negative:**
- HS256 secret must be shared if another service ever needs to verify tokens —
  at that point, migrate to RS256 or an OIDC provider
- Key rotation requires a coordinated restart (all in-flight tokens are invalidated)
- Credential storage is currently in-memory (`InMemoryUserDetailsManager`) —
  a DB-backed `UserDetailsService` is required before production use
- The app is its own identity provider — password reset, MFA, and account lockout
  must be implemented from scratch if needed; an OIDC provider gives these for free
