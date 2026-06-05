# ADR-0014: URL-Based API Versioning

**Status:** Accepted  
**Date:** 2026-06-10

## Context

The REST API needs a versioning strategy so that breaking changes can be introduced
in a future version without disrupting existing clients. A version identifier must
appear somewhere in the HTTP contract.

Three industry-standard strategies were considered:

- **URL versioning** — version embedded in the path: `/v1/buildings`, `/v2/buildings`
- **Header versioning** — version in a custom or `Accept` header:
  `Accept: application/vnd.smartcity.v2+json`
- **Query parameter versioning** — version as a query param: `/buildings?version=2`

## Decision

Use **URL versioning** with a `/v1/` prefix on all endpoints.

All routes are declared under `@RequestMapping("/v1/buildings")` in
`PublicBuildingController`. The context path `/SmartCityREST` is a deployment
concern and does not carry version semantics.

Reasons:

- **Visible and explicit** — the version is immediately apparent in browser address
  bars, server logs, and curl commands with no header inspection required
- **Zero client configuration** — any HTTP client, browser, and Swagger UI works
  without special header setup
- **Swagger UI compatible** — springdoc renders versioned paths cleanly; header
  versioning requires content negotiation config that complicates the generated spec
- **Easy to route at infrastructure level** — a reverse proxy or API gateway can
  route `/v1/` and `/v2/` to different service instances without reading headers
- **Query parameter versioning** was rejected outright — it is not idiomatic REST
  and conflicts with actual query parameters used for filtering and pagination

**Deprecation policy:** when `v2` is introduced, `v1` remains supported for a
minimum of three months. Both versions run in the same application context — no
separate deployments. The `v1` endpoints are annotated `@Deprecated` and the
OpenAPI spec marks them with `deprecated: true`.

## Consequences

**Positive:**
- Version is visible in every log line, making debugging and traffic analysis straightforward
- No tooling required to inspect or set headers — simplifies local development and testing
- Clean separation: `v1` and `v2` controllers can coexist without any routing magic

**Negative:**
- URLs change between versions — bookmarked or hard-coded URLs break when clients
  migrate, which violates the REST ideal of stable resource identifiers
- If two versions diverge significantly, duplicate controller code may accumulate;
  mitigated by delegating to shared application services and only versioning the
  web layer
