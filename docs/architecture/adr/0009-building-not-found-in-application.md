# ADR-0009: BuildingNotFoundException in Application Layer

**Status:** Accepted  
**Date:** 2026-06-03  
**Updated:** 2026-06-19

## Context

`BuildingNotFoundException` is thrown by `PublicBuildingAppService` and
`PublicBuildingQueryService` when a repository lookup returns empty.

The question is: where does this exception belong, and what should it extend?

## Decision

Keep `BuildingNotFoundException` in **`application.exception`**, extending
**`NotFoundException`** (a domain base class).

`NotFoundException` is shared by both application-layer exceptions
(`BuildingNotFoundException`) and domain-layer exceptions (`DeviceNotFoundException`).
Using a single base class allows `GlobalExceptionHandler` to map all not-found cases
to HTTP 404 with a single `@ExceptionHandler(NotFoundException.class)` — no per-subclass
handler duplication.

The ArchUnit rule `domainExceptionsLocation` carries an explicit exclusion for
`application.exception`, permitting `BuildingNotFoundException` to extend a domain
type while living in the application layer. The reason is documented in the rule
comment in `DddArchitectureTest.java`.

## Consequences

**Positive:**
- Single `@ExceptionHandler(NotFoundException.class)` covers all not-found cases
- `application.exception` can grow with other workflow exceptions without adding
  new handler methods in `GlobalExceptionHandler`
- `BuildingNotFoundException` stays in the application layer where it is thrown

**Negative:**
- `BuildingNotFoundException` extends a domain type (`NotFoundException`) while
  living in the application layer — a deliberate, documented exception to the
  strict layer rule
