# ADR-0017: Stateless Session Policy

**Status:** Accepted  
**Date:** 2026-06-17

## Context

Spring Security's default behaviour is session-based: on successful login it creates
an HTTP session (JSESSIONID cookie) and restores the `SecurityContext` from that
session on every subsequent request. Two alternatives were considered for this JWT API:

**Option A — Session-backed (default)**  
Spring manages a server-side session. The JWT could still be used for the initial
login, but the session cookie would carry authentication on subsequent requests.

**Option B — Fully stateless (`SessionCreationPolicy.STATELESS`)**  
Spring creates no HTTP session at all. Every request must carry a JWT.
The `SecurityContextHolder` is populated fresh from the token and cleared after
the response is sent.

## Decision

Use **`SessionCreationPolicy.STATELESS`** (Option B).

The API is consumed by an Angular SPA and will eventually be consumed by mobile clients.
Neither benefits from session cookies. Forcing a JWT on every request is the
correct model for a REST API with separated frontend.

Direct consequences of this choice and why each is correct:

**CSRF disabled.**  
CSRF attacks work by tricking a browser into sending a session cookie to the server
without the user's knowledge. No session cookie means no CSRF surface.
`csrf(AbstractHttpConfigurer::disable)` is a deliberate security decision here,
not a shortcut — it is the correct posture for a stateless JWT API.
A session-based setup would require CSRF tokens; the Angular frontend already
configures `withXsrfConfiguration` for that case should sessions ever be introduced.

**Horizontal scaling is trivial.**  
Session-backed auth requires sticky sessions (all requests from a user must reach
the same instance) or a shared session store (Redis). Stateless JWT requires neither —
any instance can verify a token independently. The app can scale to N replicas
behind a load balancer with no additional infrastructure.

**`SecurityContextHolder` is request-scoped.**  
Spring clears the context after each response. There is no session to restore it from.
The JWT must be sent with every request via `Authorization: Bearer <token>`.

## Consequences

**Positive:**
- No session infrastructure required — simpler deployment and horizontal scaling
- CSRF protection is not needed and is correctly disabled
- Token expiry is explicit and enforced at the token level, not the session level
- Stateless design aligns with REST constraints (each request is self-contained)

**Negative:**
- Immediate token revocation requires a blacklist (see `TokenBlacklist`) —
  a stateful session can be invalidated instantly server-side; a JWT cannot
- WebSocket connections do not send HTTP headers after the initial handshake.
  The JWT cannot be injected by the `JwtAuthFilter` during the STOMP frame exchange.
  WebSocket authentication is a separate concern from `JwtAuthFilter` — see the
  2026-07-30 amendment below for how it's actually handled.
- Access tokens have a fixed expiry window (1 hour by default). Shortening this
  reduces the revocation window but increases refresh frequency.

## Amendment — 2026-06-20: Changed from STATELESS to IF_REQUIRED

**Status:** Superseded in part

`SessionCreationPolicy` has been changed from `STATELESS` to `IF_REQUIRED`.

**Reason:** The OAuth2 Authorization Code flow (added in ADR-0019) requires a brief
HTTP session to store the `state` nonce — a random value Spring Security generates
before redirecting the browser to Google, and verifies when Google redirects back.
Without it, the CSRF protection built into the OAuth2 handshake cannot function.
`STATELESS` prevents session creation entirely, which breaks this handshake.

**Scope of the change:** `IF_REQUIRED` means Spring _may_ create a session when
needed, not that it always does. All JWT API requests are unaffected — `JwtAuthFilter`
populates `SecurityContextHolder` from the token and Spring never reads back from
a session for API calls. A session is created only during the `/oauth2/authorization/google`
→ Google → `/login/oauth2/code/google` redirect dance, and is abandoned immediately
after `OAuth2SuccessHandler` issues the JWT and redirects to the Angular frontend.

## Amendment — 2026-07-30: WebSocket authentication implemented

**Status:** Superseded in part

The "Negative" consequence above ("WebSocket authentication is a separate concern
not yet implemented") no longer holds. `/ws/**` is now `permitAll` at the HTTP layer
— the handshake itself carries no credentials, consistent with browsers being unable
to attach a custom `Authorization` header to a WebSocket/SockJS handshake request.

**Reason it isn't a `JwtAuthFilter` extension:** `JwtAuthFilter` operates on the HTTP
request layer, which ends once the socket upgrades. Authentication instead happens
one layer up, on the STOMP protocol itself: a new `StompAuthChannelInterceptor`
(`auth.webapi.filter`) intercepts `preSend` on `StompCommand.CONNECT` and reads
`Authorization` off the **STOMP CONNECT frame** — an application-level message sent
after the socket is already open, so (unlike the raw handshake) it can carry arbitrary
headers. It validates via the existing `JwtTokenService` + `TokenBlacklist` and sets
the STOMP session's Principal via `accessor.setUser(...)`; missing, invalid, or
revoked tokens reject the CONNECT with `BadCredentialsException`. Registered via
`WebSocketConfig.configureClientInboundChannel`.

**Also fixed in the same change:** the SockJS handshake endpoint had no
`.setAllowedOrigins(...)`, which is a separate check from `corsConfigurationSource()`
— Spring's SockJS service does its own origin validation, uncovered by the app's
general CORS bean. `WebSocketConfig.registerStompEndpoints` now sets the same two
origins (`http://localhost:4200`, `https://zaricu22.github.io`).

**Frontend impact:** the STOMP client must send the JWT as a `connectHeaders` option
on the client (e.g. `@stomp/stompjs`'s `Client({ connectHeaders: { Authorization: 'Bearer ' + token } })`),
not as an HTTP header on the SockJS handshake request — the frontend WS client does
not exist yet (still an unimplemented stub as of this amendment).
