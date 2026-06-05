# Architecture Overview

## Purpose

SmartCity Backend manages energy consumption and production across public buildings.
Domain: tracking buildings, their energy devices, real-time consumption, and production data.

## Bounded Context

Single bounded context: **asset**

```
com.example.smartcityback.asset
├── domain/          — pure Java, zero framework dependencies
├── application/     — orchestration, CQRS, commands, DTOs
├── infrastructure/  — JPA persistence, external adapters
└── webapi/          — REST controllers, WebSocket, exception handling
```

All production code lives inside `asset`. A second context (e.g. `balancing`) would be a sibling package with its own layer structure and its own `DddArchitectureTest`.

## Layer Map

```
┌────────────────────────────────────────────────────────────┐
│                    WebAPI / Infrastructure                  │
│   Controllers, WebSocket, JPA Entities, External Clients   │
│  ┌──────────────────────────────────────────────────────┐  │
│  │                  Application Layer                   │  │
│  │       Services, Commands, DTOs, Event Handlers       │  │
│  │  ┌────────────────────────────────────────────────┐  │  │
│  │  │                 Domain Layer                   │  │  │
│  │  │  Aggregates, Entities, Value Objects, Events   │  │  │
│  │  └────────────────────────────────────────────────┘  │  │
│  └──────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────┘
```

Dependency rule: outer layers depend on inner layers, never the reverse.
Enforced structurally by ArchUnit — see [ADR-0002](adr/0002-archunit-ddd-enforcement.md).

## Key Domain Concepts

| Concept | Class | Role |
|---|---|---|
| Aggregate root | `PublicBuilding` | Building identity, device collection, consumption invariant |
| Entity | `EnergyDevice` | Unique identity within building, mutable production rate |
| Value object | `Energy` | Immutable value + unit pair, cross-unit equality via kW normalization |
| Domain event | `ConsumptionChangedEvent`, `DeviceAddedEvent` | Published after state changes, consumed by WebSocket + audit |
| Specification | `SubsidyEligibilitySpecification` | Encapsulates eligibility business rule |

## Profiles

| Profile | Database | Purpose |
|---|---|---|
| `dev` | PostgreSQL via `${DB_URL}` env var | Local development |
| `ci` | H2 in-memory | CI OpenAPI generation and schema export |
| `test` | Testcontainers PostgreSQL | Integration and transactional tests |
| `prod` | CockroachDB / PostgreSQL (explicit driver) | Production deployment on Render |

## Architecture Decisions

All non-obvious design choices are captured as ADRs in [`adr/`](adr/).

| ADR | Decision |
|---|---|
| [0001](adr/0001-ddd-onion-architecture.md) | DDD + Onion Architecture |
| [0002](adr/0002-archunit-ddd-enforcement.md) | ArchUnit for DDD enforcement |
| [0003](adr/0003-separate-jpa-entities-from-domain.md) | Separate JPA entities from domain |
| [0004](adr/0004-kw-canonical-energy-unit.md) | kW as canonical energy unit |
| [0005](adr/0005-synchronous-domain-events.md) | Synchronous domain events |
| [0006](adr/0006-cockroachdb-for-production.md) | CockroachDB for production |
| [0007](adr/0007-websocket-handler-in-webapi.md) | WebSocket handler in webapi layer |
| [0008](adr/0008-no-service-interfaces.md) | No interfaces for application services |
| [0009](adr/0009-building-not-found-in-application.md) | BuildingNotFoundException in application layer |
| [0010](adr/0010-ai-enriched-openapi.md) | AI-enriched OpenAPI spec |
| [0011](adr/0011-mutation-testing-on-schedule.md) | Mutation testing on schedule, not every push |
| [0012](adr/0012-409-for-business-rule-violations.md) | HTTP status tier design — 422 for validation, 409 for business rule violations |
| [0013](adr/0013-reconstitution-via-domain-methods.md) | Reconstitution via static factory, not domain methods |
