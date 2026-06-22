# Authentication Flow

The API uses stateless JWT Bearer-token authentication. Every request to a protected
endpoint must include an `Authorization: Bearer <token>` header. There are no sessions
or cookies.

## User types

Three kinds of users share one in-memory registry (`InMemoryUserRegistry`):

| Type | How created | Login method |
|---|---|---|
| Pre-seeded (`admin`, `viewer`) | App startup via env vars | `POST /v1/auth/login` with username |
| Local (self-registered) | `POST /v1/auth/register` | `POST /v1/auth/login` with email |
| Google OAuth2 | Auto-created on first Google login | `/oauth2/authorization/google` only — no password |

A user who registers locally and later signs in via Google with the same email address
is recognised as the same account (the registry entry is reused, not duplicated).

## Token lifetimes

| Token         | Lifetime | Property                                    |
|---------------|----------|---------------------------------------------|
| Access token  | 1 hour   | `app.jwt.expiration-ms=3600000`             |
| Refresh token | 7 days   | `app.jwt.refresh-expiration-ms=604800000`   |

## Endpoints

Base path: `/v1/auth`  
All endpoints below are public — no `Authorization` header required unless noted.

---

### POST /v1/auth/register

Create a new local account with email and password. Issues tokens immediately — no
separate login step required.

**Request**
```json
{
  "email": "user@example.com",
  "password": "min8chars"
}
```

Validation: `email` must be a valid email address; `password` must be at least 8 characters.

**Response `201 Created`**
```json
{
  "token": "<access-token>",
  "role": "VIEWER",
  "expiresInMs": 3600000,
  "refreshToken": "<refresh-token>"
}
```

All self-registered users receive the `VIEWER` role.

**Response `409 Conflict`** — email is already registered (empty body).

**Rate limit:** 10 requests per minute per IP. Exceeding returns `429 Too Many Requests`.

---

### POST /v1/auth/login

Authenticate with username/email and password. Returns a signed JWT and a refresh token.

The `username` field accepts either:
- a plain username for pre-seeded accounts (`admin`, `viewer`)
- an email address for self-registered accounts

**Request**
```json
{
  "username": "admin",
  "password": "your-password"
}
```

**Response `200 OK`**
```json
{
  "token": "<access-token>",
  "role": "ADMIN",
  "expiresInMs": 3600000,
  "refreshToken": "<refresh-token>"
}
```

**Response `401 Unauthorized`** — wrong credentials or a Google-only account attempted
password login (empty body).

**Rate limit:** 10 requests per minute per IP. Exceeding returns `429 Too Many Requests`.

---

### POST /v1/auth/refresh

Exchange an unexpired refresh token for a new access token and a new refresh token.
The old refresh token is consumed (single-use).

**Request**
```json
{
  "refreshToken": "<refresh-token>"
}
```

**Response `200 OK`** — same shape as login response with fresh tokens.

**Response `401 Unauthorized`** — token not found or already consumed/expired.

**Rate limit:** shared with `/login` — 10 requests per minute per IP.

---

### POST /v1/auth/logout

Revoke the current access token and its paired refresh token.
Requires a valid `Authorization: Bearer <token>` header.

**Request**
```json
{
  "refreshToken": "<refresh-token>"
}
```

**Response `204 No Content`**

After logout:
- The access token's `jti` (JWT ID) is added to an in-memory blacklist and rejected until it expires naturally.
- The refresh token is consumed from the store and cannot be reused.

---

## Google OAuth2 social login

The flow is browser-driven — not a REST API call.

**Step 1 — Initiate login**

Redirect the browser (or open a popup) to:
```
GET /oauth2/authorization/google
```
Spring Security handles the redirect to Google's consent screen automatically.

**Step 2 — Google callback**

After the user grants consent, Google redirects back to the server's OAuth2 callback URL.
Spring Security validates the ID token, then `OAuth2SuccessHandler` runs:

1. Extracts the `email` claim from the Google principal.
2. Looks up or creates the user in the registry (`findOrRegisterOAuth` — idempotent).
3. Issues a JWT + refresh token in the same format as `POST /v1/auth/login`.
4. Redirects the browser to the frontend callback URL with credentials in the URL fragment (`#`).

**Step 3 — Frontend receives tokens**

The redirect lands at `app.oauth2.frontend-callback-url` with tokens in the fragment:
```
https://<frontend>/auth/callback#token=<jwt>&role=VIEWER&expiresInMs=3600000&refreshToken=<refresh>
```

The fragment is never sent to any server, so tokens do not appear in access logs or
`Referer` headers.

All OAuth2 users receive the `VIEWER` role.

---

## Using the token

Add the access token to every subsequent request:

```
Authorization: Bearer <access-token>
```

## Role-based access

| Role     | Permitted operations                                          |
|----------|---------------------------------------------------------------|
| `ADMIN`  | All endpoints — read and write                                |
| `VIEWER` | Read-only — `GET /v1/buildings` and `GET /v1/buildings/{id}` |

Method-level access is enforced with `@PreAuthorize` annotations on the controllers.
Self-registered and OAuth2 users always start as `VIEWER`.

## Token internals (HS256)

The JWT is signed with HMAC-SHA256 using a base64-encoded secret (`JWT_SECRET` env var,
minimum 32 bytes). Claims included:

| Claim  | Value                                |
|--------|--------------------------------------|
| `sub`  | username or email                    |
| `role` | `ADMIN` or `VIEWER`                  |
| `jti`  | random UUID — used for revocation    |
| `iat`  | issued-at timestamp                  |
| `exp`  | expiry timestamp                     |

## Typical session flows

### Login (credential verification)
```
Client                 AuthController         AuthenticationManager      InMemoryUserRegistry
  |                         |                         |                          |
  |-- POST /v1/auth/login ->|                         |                          |
  |   { username, password }|                         |                          |
  |                         |-- authenticate() ------->|                          |
  |                         |   UsernamePassword-      |                          |
  |                         |   AuthenticationToken    |                          |
  |                         |                         |-- loadUserByUsername() -->|
  |                         |                         |<-- UserDetails -----------|
  |                         |                         |                          |
  |                         |                         |-- BCrypt.matches() ------>
  |                         |                         |<-- true / false ----------
  |                         |                         |
  |                         |<-- Authentication -------|
  |                         |   (principal + role)     |
  |                         |                          |
  |                         |-- JwtTokenService.generate(username, role)
  |                         |-- RefreshTokenStore.issue(username, role)
  |                         |                          |
  |<-- 200 { token, role, expiresInMs, refreshToken } -|
```

### Local registration
```
Client                  AuthController         InMemoryUserRegistry    JwtTokenService    RefreshTokenStore
  |                          |                        |                      |                   |
  |-- POST /v1/auth/ --->    |                        |                      |                   |
  |   register               |                        |                      |                   |
  |   { email, password }    |                        |                      |                   |
  |                          |-- register(email, ---->|                      |                   |
  |                          |   rawPassword)         |                      |                   |
  |                          |                        |-- BCrypt.encode()    |                   |
  |                          |                        |-- putIfAbsent()      |                   |
  |                          |                        |   (duplicate → 409)  |                   |
  |                          |                        |                      |                   |
  |                          |-- generate(email, ----------------------->    |                   |
  |                          |   "VIEWER")            |                      |                   |
  |                          |<-- JWT token ---------------------------------|                   |
  |                          |                        |                      |                   |
  |                          |-- issue(email, ------------------------------------------------>  |
  |                          |   "VIEWER")            |                      |                   |
  |                          |<-- refreshToken --------------------------------------------------|
  |                          |                        |                      |                   |
  |<-- 201 { token, role: VIEWER, expiresInMs, refreshToken }  ------        |                   |
  |                          |                        |                      |                   |
  |-- GET /v1/buildings                               |                      |                   |
  |   (Authorization: Bearer token) -------------->   |                      |                   |
  |<-- 200 buildings ----    |                        |                      |                   |
  |                          |                        |                      |                   |
  |   ... token expires ...  |                        |                      |                   |
  |                          |                        |                      |                   |
  |-- POST /v1/auth/refresh (refreshToken) ------->   |                      |                   |
  |<-- 200 { newToken, newRefresh } ---------------   |                      |                   |
  |                          |                        |                      |                   |
  |-- POST /v1/auth/logout (Bearer + refreshToken)>   |                      |                   |
  |<-- 204 No Content   ---- |                        |                      |                   |
```

### Google OAuth2
```
Browser            Spring Security       OAuth2SuccessHandler    InMemoryUserRegistry      Google
  |                      |                       |                       |                    |
  |-- GET /oauth2/       |                       |                       |                    |
  |   authorization/  -->|                       |                       |                    |
  |   google             |                       |                       |                    |
  |<-- 302 redirect -----|                       |                       |                    |
  |   to Google          |                       |                       |                    |
  |                                                                                           |
  |-- GET accounts.google.com/o/oauth2/auth... ---------------------------------------------->|
  |<-- consent screen ------------------------------------------------------------------------|
  |-- user grants access -------------------------------------------------------------------->|
  |<-- 302 redirect to /login/oauth2/code/google ---------------------------------------------|
  |                      |                       |                       |                    |
  |-- GET /login/        |                       |                       |                    |
  |   oauth2/code/google>|                       |                       |                    |
  |                      |-- validate token ------------------------------------------------->|
  |                      |<-- user info (email) ----------------------------------------------|
  |                      |                       |                       |                    |
  |                      |-- onAuthentication -->|                       |                    |
  |                      |   Success()           |                       |                    |
  |                      |                       |-- findOrRegister ---->|                    |
  |                      |                       |   OAuth(email)        |                    |
  |                      |                       |<-- RegisteredUser ----|                    |
  |                      |                       |                       |                    |
  |                      |                       |-- JwtTokenService.generate(email, role)    |
  |                      |                       |-- RefreshTokenStore.issue(email, role)     |
  |                      |                       |                       |                    |
  |<-- 302 redirect to frontend#token=...&role=...&refreshToken=...   ---|                    |
  |                      |                       |                       |                    |
  |-- GET /v1/buildings (Authorization: Bearer token) -->  |             |                    |
  |<-- 200 buildings ----|                       |                       |                    |
```
