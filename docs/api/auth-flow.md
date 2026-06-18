# Authentication Flow

The API uses stateless JWT Bearer-token authentication. Every request to a protected
endpoint must include an `Authorization: Bearer <token>` header. There are no sessions
or cookies.

## Credentials

Two in-memory users are pre-configured at startup via environment variables:

| Username | Role    | Password env var     |
|----------|---------|----------------------|
| `admin`  | `ADMIN` | `ADMIN_PASSWORD`     |
| `viewer` | `VIEWER`| `VIEWER_PASSWORD`    |

## Token lifetimes

| Token         | Lifetime       | Property                        |
|---------------|----------------|---------------------------------|
| Access token  | 1 hour         | `app.jwt.expiration-ms=3600000` |
| Refresh token | 7 days         | `app.jwt.refresh-expiration-ms=604800000` |

## Endpoints

Base path: `/v1/auth`  
These endpoints are public — no `Authorization` header required.

---

### POST /v1/auth/login

Authenticate with username and password. Returns a signed JWT and a refresh token.

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

**Response `401 Unauthorized`** — wrong username or password (empty body).

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

## Using the token

Add the access token to every subsequent request:

```
Authorization: Bearer <access-token>
```

## Role-based access

| Role     | Permitted operations                              |
|----------|---------------------------------------------------|
| `ADMIN`  | All endpoints — read and write                    |
| `VIEWER` | Read-only — `GET /v1/buildings` and `GET /v1/buildings/{id}` |

Method-level access is enforced with `@PreAuthorize` annotations on the controllers.

## Token internals (HS256)

The JWT is signed with HMAC-SHA256 using a base64-encoded secret (`JWT_SECRET` env var,
minimum 32 bytes). Claims included:

| Claim  | Value                          |
|--------|--------------------------------|
| `sub`  | username                       |
| `role` | `ADMIN` or `VIEWER`            |
| `jti`  | random UUID — used for revocation |
| `iat`  | issued-at timestamp            |
| `exp`  | expiry timestamp               |

## Typical session flow

```
Client                          Server
  |                               |
  |-- POST /v1/auth/login ------->|
  |<-- { token, refreshToken } ---|
  |                               |
  |-- GET /v1/buildings           |
  |   Authorization: Bearer token>|
  |<-- 200 buildings -------------|
  |                               |
  |   ... token expires ...       |
  |                               |
  |-- POST /v1/auth/refresh ------>|
  |<-- { newToken, newRefresh } --|
  |                               |
  |-- POST /v1/auth/logout ------->|
  |<-- 204 No Content ------------|
```
