# ADR-0001: DDD + Onion Architecture

**Status:** Accepted  
**Date:** 2026-05-04

## Context

The project serves as both a production backend and an educational DDD reference.
The architecture needed to enforce domain purity, make business rules explicit, and
demonstrate correct layer separation in a way that scales to a team.

## Decision

Apply **Domain-Driven Design (DDD)** with **Onion Architecture**
(usually misconfused with Clean and Hexagonal architectures, more suitable for experienced programmers):

```
domain → application → infrastructure
                     → webapi
```

- `domain` — pure Java, zero framework annotations, holds all business rules
- `application` — orchestrates domain objects, implements use cases; 
    uses **pragmatic CQRS**: a dedicated `QueryService` handles reads while `AppService` handles writes — no separate read model or event sourcing 
- `infrastructure` — JPA persistence, external service clients
- `webapi` — REST controllers, WebSocket handlers, exception handling, explicit request/response type separation

Dependencies point inward only. `domain` knows nothing about Spring, JPA, or HTTP.

Package structure follows **Package-by-Bounded-Context**, not Package-by-Layer:
each bounded context (`asset`) owns its full vertical slice of all four layers.

## Consequences

**Positive:**
- Domain logic is portable — no framework lock-in at the core
- Business rules are testable without Spring context or database
- Clear ownership: adding a feature touches domain → application → webapi in sequence
- A new bounded context (`balancing`, `reporting`) adds a sibling package, not new modules

**Negative:**
- More files and indirection than a plain Spring layered app
- Mappers required at each layer boundary (domain ↔ infrastructure, domain ↔ webapi)
- Developers unfamiliar with DDD have a steeper initial learning curve

**Enforcement:** Layer dependency rules are verified at compile time by ArchUnit — see ADR-0002.
