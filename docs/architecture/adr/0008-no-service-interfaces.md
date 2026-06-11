# ADR-0008: No Interfaces for Application Services

**Status:** Accepted  
**Date:** 2026-06-03

## Context

The Dependency Inversion Principle (DIP) suggests that callers should depend on
abstractions (interfaces), not concrete classes. In hexagonal architecture, application
service interfaces act as "ports" — the controller depends on the port, not the
implementation.

In this project, `PublicBuildingAppService` and `PublicBuildingQueryService` are
injected directly into `PublicBuildingController`.

## Decision

**No interfaces for application services.** Concrete classes are injected directly.

Reasons:
- Interfaces serve a purpose when the **dependency direction must be inverted**: domain
  repository interfaces exist because `application` cannot depend on `infrastructure`, so
  the interface is placed in `domain` and `infrastructure` implements it — flipping the
  natural dependency. `webapi → application` is already the correct and intended dependency
  direction; there is nothing to invert, so an interface would be boilerplate with no
  architectural function.
- Single module, single bounded context — there is exactly one implementation of each
  service and there are no plans for alternatives
- Spring's DI and `@MockitoBean` work with concrete classes — testability is not affected
- Interfaces would add two files with no behaviour, purely as boilerplate
- The `applicationServicesMustHaveInterface` ArchUnit rule has been removed accordingly

## Consequences

**Positive:**
- Less boilerplate — no `IPublicBuildingAppService` / `IPublicBuildingQueryService` files
- Controller and service are in the same module — the coupling is intentional and visible

**Negative:**
- If a second implementation is ever needed (e.g. a caching decorator), an interface
  must be extracted at that point — minor refactor but requires touching the controller
- Violates strict DIP — acceptable trade-off for a single-module project
