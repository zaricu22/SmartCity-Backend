# ADR-0015: CQRS Split — Separate AppService and QueryService

**Status:** Accepted  
**Date:** 2026-06-10

## Context

The application layer must handle both state-changing operations (create building,
add device, change consumption, change production) and read operations (get by ID,
get all). These have fundamentally different transactional and performance
characteristics.

Alternatives considered:

- **Single service class** — one `PublicBuildingService` handles all commands and
  queries under a single `@Transactional` annotation
- **Service-level CQRS split** — two classes: one for commands (`@Transactional`),
  one for queries (`@Transactional(readOnly=true)`)
- **Full CQRS** — separate read model, separate read-side repository, event-sourced
  write model, eventual consistency between write and read stores

## Decision

Use a **service-level CQRS split** with two classes:

- `PublicBuildingAppService` — write side; annotated `@Transactional` at class level;
  handles `create`, `addDevice`, `changeConsumption`, `changeProduction`
- `PublicBuildingQueryService` — read side; annotated `@Transactional(readOnly=true)`
  at class level; handles `getById`, `getAll`; returns DTOs directly

This is a **lightweight CQRS** — the split is at the service class level only.
There is no separate read model, no separate database, and no eventual consistency.
Both services use the same `PublicBuildingRepository` and the same JPA entities.

Reasons:

- `@Transactional(readOnly=true)` is a meaningful optimization: Hibernate skips
  dirty checking on all loaded entities, the JDBC driver can use read-only
  connection hints, and CockroachDB can route the query to a follower replica.
  A single `@Transactional` class forces write-level overhead on every read.
- Commands and queries have different failure modes: a command that violates a
  domain invariant throws a business exception; a query that finds nothing throws
  a `BuildingNotFoundException`. Separating them makes each class's responsibility
  and exception surface explicit.
- Full CQRS (separate read model) was rejected: this is a single bounded context
  with one aggregate. The added complexity of maintaining two synchronized data
  stores is not justified by the current read load or query complexity.

## Consequences

**Positive:**
- Read transactions are explicitly optimized — `readOnly=true` is enforced at the
  class level, not left to per-method discipline
- Each service class has a single axis of change: `AppService` changes when
  commands change; `QueryService` changes when read requirements change
- Both classes can be tested independently — `AppService` tests mock the repository
  and verify domain event publishing; `QueryService` tests verify DTO mapping
  and query delegation

**Negative:**
- `PublicBuildingController` holds two injected dependencies instead of one;
  a `PublicBuildingFacade` in the application layer would hide the split from
  the web layer — deferred as unnecessary abstraction while there is only one controller
- If a command needs to return enriched data (e.g. the created building, not just
  its ID), it must either delegate to `QueryService` internally or duplicate the
  mapping logic — the current design returns only the ID from commands, consistent
  with CQRS convention
