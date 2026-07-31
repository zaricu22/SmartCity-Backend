# Architecture Overview

## Purpose

SmartCity Backend manages energy consumption and production across public buildings.
Domain: tracking buildings, their energy devices, real-time consumption, and production data.

## Bounded Contexts

Two bounded contexts:

**asset** — energy domain (DDD/Onion)
```
com.example.smartcityback.asset
├── domain/          — pure Java, zero framework dependencies
├── application/     — orchestration, CQRS, commands, DTOs
├── infrastructure/  — JPA persistence, external adapters
└── webapi/          — REST controllers, WebSocket, exception handling
```

**auth** — identity and access (flat, no domain layer)
```
com.example.smartcityback.auth
├── infrastructure/  — JWT, user registry, token store, blacklist
└── webapi/          — AuthController, OAuth2SuccessHandler, filters, DTOs
```

Additional contexts (e.g. `balancing`) would be sibling packages with their own layer structure and `DddArchitectureTest`.

```
┌──────────────────────────────────────────┐   ┌──────────────────────────────────────────┐
│           «Bounded Context»               │   │           «Bounded Context»               │
│                asset                      │   │                auth                       │
│                                           │   │                                           │
│  Full DDD / Onion Architecture            │   │  Flat — no domain model                  │
│                                           │   │                                           │
│  Domain:                                  │   │  InMemoryUserRegistry                     │
│    PublicBuilding  (Aggregate Root)       │   │  JwtTokenService                          │
│    EnergyDevice    (Entity)               │   │  RefreshTokenStore (rotating tokens)      │
│    Energy          (Value Object)         │   │  TokenBlacklist  (@Scheduled eviction)    │
│    SubsidyEligibilitySpecification        │   │  OAuth2SuccessHandler                     │
│    Domain Events (3)                      │   │                                           │
│                                           │   │  Endpoints:                               │
│  Application:                             │   │    POST /v1/auth/register                 │
│    PublicBuildingAppService               │   │    POST /v1/auth/login                    │
│    PublicBuildingQueryService             │   │    POST /v1/auth/refresh                  │
│                                           │   │    POST /v1/auth/logout                   │
│  Endpoints:                               │   │    GET  /oauth2/authorization/google      │
│    /v1/buildings/**                       │   │    GET  /login/oauth2/code/google         │
│                                           │   │                                           │
└──────────────────┬───────────────────────┘   └──────────────────┬───────────────────────┘
                   │                                               │
                   └─────────────── shared ─────────────────────┘
                                SecurityConfig  ·  GlobalExceptionHandler
                                RequestIdFilter  ·  WebSocket config
                                  (single Spring Boot deployable)
```

## Layer Map

```
╔══════════════════════════════════════════════════════════════════════════╗
║                    Infrastructure  ·  Web API                            ║
║                                                                          ║
║   PublicBuildingRepositoryImpl     PublicBuildingController              ║
║   PublicBuildingJpaEntity          AuthController                        ║
║   EnergyDeviceJpaEntity            GlobalExceptionHandler                ║
║   EnergyEmbeddable                 RequestIdFilter                       ║
║   PublicBuildingMapper             JwtAuthFilter  ·  RateLimitFilter     ║
║   SubsidyEligibilityJpaSpec        BuildingWebSocketEventHandler         ║
║   JwtTokenService                  OAuth2SuccessHandler                  ║
║   RefreshTokenStore · TokenBlacklist · InMemoryUserRegistry              ║
║                                                                          ║
║  ┌────────────────────────────────────────────────────────────────────┐  ║
║  │                          Application                                │  ║
║  │                                                                     │  ║
║  │   PublicBuildingAppService (@Transactional write)                   │  ║
║  │   PublicBuildingQueryService (@Transactional readOnly)              │  ║
║  │   AddDeviceCommand  ·  CreateBuildingCommand (records)              │  ║
║  │   ChangeConsumptionCommand  ·  ChangeProductionCommand              │  ║
║  │   AuditLogEventHandler  ·  BuildingDtoMapper                        │  ║
║  │                                                                     │  ║
║  │  ┌───────────────────────────────────────────────────────────────┐  │  ║
║  │  │                           Domain                               │  │  ║
║  │  │                                                                │  │  ║
║  │  │   PublicBuilding (Aggregate Root)                              │  │  ║
║  │  │   EnergyDevice (Entity)                                        │  │  ║
║  │  │   Energy (Value Object)                                        │  │  ║
║  │  │   BuildingCreatedEvent  ·  BuildingDeletedEvent                │  │  ║
║  │  │   DeviceAddedEvent  ·  DeviceRemovedEvent                      │  │  ║
║  │  │   ConsumptionChangedEvent  ·  ProductionChangedEvent           │  │  ║
║  │  │   DomainEvent (marker)                                        │  │  ║
║  │  │   PublicBuildingRepository (interface — implemented in infra)  │  │  ║
║  │  │   SubsidyEligibilitySpecification                              │  │  ║
║  │  │   PublicBuildingSummary (readmodel — query projection shape)   │  │  ║
║  │  │   DomainException hierarchy                                    │  │  ║
║  │  └───────────────────────────────────────────────────────────────┘  │  ║
║  └────────────────────────────────────────────────────────────────────┘  ║
╚══════════════════════════════════════════════════════════════════════════╝
                  Dependencies always point inward  →
```

Dependency rule: outer layers depend on inner layers, never the reverse.
Enforced structurally by ArchUnit — see [ADR-0002](adr/0002-archunit-ddd-enforcement.md).

## Key Domain Concepts

| Concept | Class | Role |
|---|---|---|
| Aggregate root | `PublicBuilding` | Building identity, device collection, consumption invariant |
| Entity | `EnergyDevice` | Unique identity within building, mutable production rate |
| Value object | `Energy` | Immutable value + unit pair, cross-unit equality via kW normalization |
| Domain event | `ConsumptionChangedEvent`, `DeviceAddedEvent`, `DeviceRemovedEvent`, `ProductionChangedEvent` | Published after state changes, consumed by WebSocket (per-building topic) + audit |
| Domain event | `BuildingCreatedEvent`, `BuildingDeletedEvent` | Published on aggregate construction/deletion, consumed by WebSocket (collection-level `/topic/buildings`, `/topic/buildings/deleted` — no id exists to subscribe per-building before creation, and none remains after deletion) + audit |
| Specification | `SubsidyEligibilitySpecification` | Encapsulates eligibility business rule — backs `GET /v1/buildings?eligible=true` via a query projection, not a full-entity load |

## Aggregate Boundary

```
╔═══════════════════════════════════════════════════════════════════════════════╗
║                           «Aggregate Root»                                    ║
║                            PublicBuilding                                     ║
║                                                                               ║
║   id: UUID                name: String              location: String          ║
║                                                                               ║
║   consumption: ────────── «Value Object» ─────────────────────────────────┐  ║
║                            Energy                                          │  ║
║                            value: BigDecimal                               │  ║
║                            unit: EnergyUnit (kW / MW / GW)                │  ║
║                            to(unit) · greaterThan() · compareTo()         │  ║
║                            equals/hashCode on normalised kW value ─────────┘  ║
║                                                                               ║
║   devices: List<EnergyDevice>                                                 ║
║   │                                                                           ║
║   └─── «Entity» ──────────────────────────────────────────────────────────┐  ║
║          EnergyDevice                                                      │  ║
║          id: UUID                                                          │  ║
║          name: String  (required, non-empty)                              │  ║
║          type: DeviceType  (SOLAR · WIND · ...)                            │  ║
║          deviceRatedCapacity: Energy  (final — set once)                   │  ║
║          productionRate: Energy                                             │  ║
║          changeProduction() — enforces productionRate ≤ ratedCapacity      │  ║
║          equals/hashCode on id only ───────────────────────────────────────┘  ║
║                                                                               ║
║  ─────────────────────────────────────────────────────────────────────────   ║
║  Invariants enforced at aggregate boundary:                                   ║
║  · addDevice()              — total capacity across all devices ≤ limit       ║
║  · removeDevice()           — device must exist (DeviceNotFoundException)    ║
║  · changeConsumption()      — value must be positive                          ║
║  · changeDeviceProduction() — must not exceed that device's ratedCapacity    ║
║                                                                               ║
║  Domain Events (collected, published after save — never before):              ║
║    BuildingCreatedEvent · DeviceAddedEvent · DeviceRemovedEvent               ║
║    ConsumptionChangedEvent · ProductionChangedEvent                           ║
║    pullEvents() → List.copyOf(snapshot) then clear — prevents re-publish     ║
║    NOTE: BuildingDeletedEvent is NOT in this list — it's published directly  ║
║    by AppService.delete(), not raised by the aggregate (no invariant to      ║
║    protect; the aggregate ceases to exist rather than being saved)           ║
╚═══════════════════════════════════════════════════════════════════════════════╝
                  │
                  │  PublicBuildingRepository  (domain interface)
                  │  implemented by PublicBuildingRepositoryImpl (infra)
                  ▼
         PublicBuildingJpaEntity   (never crosses the aggregate boundary)
```

See [erd.md](erd.md) for the database table structure that backs this aggregate.

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
| [0014](adr/0014-url-based-api-versioning.md) | URL-based API versioning (`/v1/`) |
| [0015](adr/0015-cqrs-split-app-service-query-service.md) | CQRS split — separate AppService and QueryService |
| [0016](adr/0016-self-issued-jwt-hs256.md) | Self-issued JWT with HS256 over OAuth2/OIDC provider |
| [0017](adr/0017-stateless-session-policy.md) | Stateless session policy |
| [0018](adr/0018-pagination-and-sorting.md) | Pagination and sorting strategy |
| [0019](adr/0019-user-registration-and-oauth2-social-login.md) | User registration, OAuth2 social login, and in-memory identity store |
