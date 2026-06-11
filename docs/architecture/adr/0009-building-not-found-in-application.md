# ADR-0009: BuildingNotFoundException in Application Layer

**Status:** Accepted  
**Date:** 2026-06-03

## Context

`BuildingNotFoundException` extends `NotFoundException` (a domain exception) but is
thrown by `PublicBuildingAppService` and `PublicBuildingQueryService` when a repository
lookup returns empty.

The ArchUnit rule `domainExceptionsLocation` flags all `DomainException` subclasses
that live outside `domain.exception`. The question is: where does this exception belong?

## Decision

Keep `BuildingNotFoundException` in **`application.exception`**.

"Building not found" is an **application workflow concern**, not a domain invariant:
- The domain aggregate `PublicBuilding` never throws "building not found" — it has
  no knowledge of persistence or the existence of other buildings
- The exception is thrown by the application service when orchestrating a repository
  lookup — that is an application-layer responsibility
- Domain exceptions (`DeviceAlreadyExistsException`, `BuildingTotalCapacityExceededException`)
  are thrown by domain objects enforcing their own invariants — a fundamentally different concept

The `domainExceptionsLocation` ArchUnit rule excludes `application.exception`, 
because some `domain.exception` subclasses can be reasonable located in `application.exception`,
with a comment explaining this design intention.

## Consequences

**Positive:**
- The distinction between domain invariant exceptions and application workflow exceptions
  is explicit and enforced by package location
- `application.exception` can grow with other workflow exceptions without polluting `domain.exception`

**Negative:**
- `BuildingNotFoundException extends NotFoundException` (a domain base class) — the inheritance
  crosses the layer boundary, which is a type inconsistency
- `ApplicationException` already exists as an abstract base class in `application.exception`
  and would be the correct parent. The reason `BuildingNotFoundException` still extends
  `NotFoundException` is that `GlobalExceptionHandler.handleNotFound()` maps all
  `NotFoundException` subclasses to HTTP 404. Switching the parent to `ApplicationException`
  would require adding a dedicated `@ExceptionHandler(ApplicationException.class)` in
  `GlobalExceptionHandler` — deferred as unnecessary complexity for the current single exception
