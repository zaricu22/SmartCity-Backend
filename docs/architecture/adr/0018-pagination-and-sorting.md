# ADR-0018: Pagination and Sorting Strategy

**Status:** Accepted  
**Date:** 2026-06-19

## Context

`GET /v1/buildings` must support pagination and sorting so that clients can page
through large building lists without loading the full dataset. The frontend already
renders a paginated list and needs metadata (total elements, total pages, current page)
to drive its UI controls.

Two structural questions arise:
1. **Wire format** — how does the API communicate pagination parameters and return page metadata?
2. **Type placement** — where do the pagination types live in the DDD layer structure?

## Decision

### 1. URL-based query parameters (Spring `Pageable`)

Pagination state is passed as standard query parameters:

```
GET /v1/buildings?page=0&size=20&sort=name,asc
```

Spring's `Pageable` abstraction is used on the controller with `@PageableDefault` and
`@ParameterObject` (springdoc) so that Swagger UI exposes the parameters correctly.
The controller extracts the first sort order and delegates plain `int`/`String` values
to the query service — keeping Spring's `Pageable` out of the application and domain layers.

### 2. Two-type pagination model

| Type | Package | Purpose |
|---|---|---|
| `PagedResult<T>` | `asset.shared` | Bounded-context shared kernel; pure Java record; used by domain repository contract and application service |
| `PagedResponse<T>` | `webapi.response` | HTTP wire format; serialized to JSON; mirrors the frontend's `PageResponse<T>` |

`PagedResult<T>` lives in `asset.shared` — a package declared **outside** all four
ArchUnit-defined layers (`domain`, `application`, `infrastructure`, `webapi`). Because
`layerDependencies` uses `consideringOnlyDependenciesInLayers()`, access to `asset.shared`
from any layer requires no `ignoreDependency` exception. Rule 32 (`sharedMustNotDependOnAnyLayer`)
enforces that nothing flows back from any layer into `asset.shared`.

`PagedResponse<T>` is a separate webapi type, so the ArchUnit `responsePlacement` rule
places it correctly in `webapi.response` automatically.

The mapper `BuildingResponseMapper.toResponsePage()` converts `PagedResult<PublicBuildingDto>`
→ `PagedResponse<PublicBuildingResponse>` at the webapi boundary.

### 3. Infrastructure implementation

`PublicBuildingJpaRepository` extends both `JpaRepository` and `JpaSpecificationExecutor`
(wired but unused until filtering is added). `PublicBuildingRepositoryImpl.findAll()` uses
Spring Data's `PageRequest.of(page, size, Sort.by(direction, sortBy))` and maps the
returned `Page<T>` to `PagedResult<T>`.

### 4. Frontend alignment

The frontend uses the same two-type approach:
- `Page<T>` in `asset/shared/page.ts` — shared domain concept
- `PageResponse<T>` in `asset/infrastructure/http/page.response.ts` — HTTP wire type

The backend mirrors this structure: `PagedResult<T>` in `asset.shared`,
`PagedResponse<T>` in `webapi.response`.

## Consequences

**Positive:**
- Standard Spring `Pageable` query params (`?page`, `?size`, `?sort`) are idiomatic
  and well-supported by Spring Data, springdoc, and all HTTP clients
- `PagedResult<T>` as a pure Java record in the shared kernel keeps the domain
  repository contract and application layer free of framework types
- Two-type separation ensures the HTTP wire format can evolve independently of the
  domain concept
- `JpaSpecificationExecutor` is already wired, making filter-by-field queries addable
  without interface changes

**Negative:**
- Each new sortable field must be a valid JPA column name; invalid `sort` values
  cause a `PropertyReferenceException` at runtime (not validated at the controller boundary)
- The controller extracts the first sort order only; multi-column sorting is not
  surfaced to the application layer without a further change
