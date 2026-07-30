# ADR-0020: Query Projection for `GET /v1/buildings?eligible=true`

**Status:** Accepted
**Date:** 2026-07-29

## Context

`SubsidyEligibilityJpaSpecification` (Criteria API, `Specification<PublicBuildingJpaEntity>`)
existed for several sessions with no caller — `PublicBuildingJpaRepository` extended
`JpaSpecificationExecutor`, but nothing invoked `findAll(Specification, Pageable)`. Wiring it
up to back a real endpoint (`GET /v1/buildings?eligible=true`) surfaced two decisions worth
recording.

## Decision

### 1. A manual `CriteriaQuery` instead of `JpaSpecificationExecutor`

`JpaSpecificationExecutor.findAll(Specification<T>, Pageable)` is hard-typed to return
`Page<T>` — here, `Page<PublicBuildingJpaEntity>`. It cannot produce a projected result shape;
the interface has no overload that accepts both a `Specification` and a target projection type.

Using it as-is would mean loading the **full** `PublicBuildingJpaEntity` (every column, plus
triggering the `devices` collection if accessed) for a list view that only needs
`id`/`name`/`location`/`consumption` — defeating the point of adding a projection.

Instead, `PublicBuildingRepositoryImpl.findEligibleForSubsidy()` builds a manual
`CriteriaQuery<PublicBuildingSummary>` via `EntityManager`:
- `cb.construct(PublicBuildingSummary.class, id, name, location, consumption.value, consumption.unit)`
  as the `SELECT`
- `SubsidyEligibilityJpaSpecification#toPredicate(root, query, cb)` reused directly for the
  `WHERE` clause — the `Specification` object still does the filtering logic; only the query
  *shape* around it changes
- `setFirstResult`/`setMaxResults` for paging, plus a second `COUNT` query (same predicate) for
  `totalElements`, since `Page<T>`'s automatic count query isn't available outside
  `JpaSpecificationExecutor`

`PublicBuildingSummary` lives in a new domain package, `domain.readmodel` — not
`domain.repository` (ArchUnit's `repositoryNaming` rule requires every class there to end with
`Repository`) and not `application.dto` (domain layer cannot depend on the application layer;
`dtoPlacement` requires `*Dto` classes in `application.dto`). It is a plain record with no
behavior, mapped in `PublicBuildingQueryService` into the existing `PublicBuildingDto` (with
`devices = List.of()`), so the controller, response types, and JSON contract for
`GET /v1/buildings` are unchanged regardless of the `eligible` flag.

### 2. `EntityManager` is field-injected, not constructor-injected

This project uses constructor injection everywhere, with no `@Autowired`/field injection
elsewhere. `EntityManager` is the one exception:
`@PersistenceContext`'s `@Target` is `{TYPE, METHOD, FIELD}` — it cannot annotate a constructor
parameter, so there is no way to constructor-inject a JPA-managed `EntityManager` proxy the
way every other dependency in this codebase is injected. `PublicBuildingRepositoryImpl` field-
injects it (`@PersistenceContext private EntityManager entityManager;`) as a deliberate,
framework-mandated exception, not a stylistic regression.

## Consequences

**Positive:**
- `GET /v1/buildings?eligible=true` executes a genuine projection — the SQL `SELECT` only
  fetches the five needed columns, `energy_device` is never joined or loaded
- `SubsidyEligibilityJpaSpecification`'s predicate logic is reused as-is; the eligibility rule
  is still defined in exactly one place
- The webapi-facing contract (`PublicBuildingResponse`/`PagedResponse`) is unchanged — only the
  `eligible=true` branch always returns an empty `devices` list, reflecting that the query
  never fetches them

**Negative:**
- Pagination for this one method is hand-rolled (manual count query, manual offset/limit)
  instead of reusing `Page<T>`/`PageRequest`, so it diverges from the pattern in
  `findAll()`/[ADR-0018](0018-pagination-and-sorting.md)
- `JpaSpecificationExecutor` remains only useful for full-entity queries; any future
  projection + filter combination will need the same manual-`CriteriaQuery` approach, not a
  reusable abstraction
- One field-injected dependency now exists in a codebase that otherwise enforces constructor
  injection by convention (not by ArchUnit rule — nothing currently prevents further field
  injection from creeping in elsewhere)
