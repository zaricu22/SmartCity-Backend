# ADR-0002: ArchUnit for DDD Enforcement

**Status:** Accepted  
**Date:** 2026-06-03

## Context

Onion Architecture rules (domain has no outward dependencies, webapi does not access
domain entities directly, etc.) are easy to violate accidentally — a wrong import
compiles fine but breaks the architecture. Code reviews alone are not reliable enough
to catch these violations consistently.

## Decision

Use **ArchUnit** (`archunit-junit5:1.3.0`) to enforce layer rules as JUnit 5 tests.

Rules live in `DddArchitectureTest.java`, scoped to the `asset` bounded context.
When a second bounded context is added, a separate test file is created for it.

Rules run in a dedicated `architecture` CI job — parallel to `test`, no DB or secrets
needed, fails fast before expensive jobs finish.

### All 21 enforced rules

| # | Rule field | What it checks |
|---|---|---|
| 1 | `layerDependencies` | Domain → none; Application → Domain; Infrastructure → Domain+Application; WebApi → Application (with deliberate exceptions below) |
| 2 | `noSpringAnnotationsInDomain` | No `@Service`, `@Component`, `@Repository` in domain |
| 3 | `noJpaAnnotationsInDomain` | No `@Entity`, `@Table`, `@MappedSuperclass` in domain |
| 4 | `jpaEntitiesOnlyInInfrastructure` | `@Entity` classes may only live in `infrastructure.persistence.entity` |
| 5 | `internalEntitiesHiddenFromWebApi` | `webapi` layer may not reference `domain.entity` classes — webapi only, because the application layer legitimately maps through domain entities |
| 6 | `repositoryInterfacesInDomain` | Repository interfaces (excluding Spring Data JPA repos) must live in `domain.repository` |
| 7 | `repositoryImplementationsInInfrastructure` | Classes that implement a domain repository interface must live in `infrastructure` |
| 8 | `domainServicesMustNotDependOnRepository` | `domain.service` classes must not inject any `*Repository` — `allowEmptyShould(true)` because no domain services exist yet; acts as a guard for when they are added |
| 9 | `noPublicSettersInDomain` | No `public set*` methods in domain classes — `allowEmptyShould(true)` because domain already has no setters; prevents future regression |
| 10 | `valueObjectFieldsMustBeFinal` | All non-static fields in `domain.valueobject` must be `final` |
| 11 | `valueObjectsMustOverrideEqualsAndHashCode` | Every value object must override both `equals()` and `hashCode()` |
| 12 | `domainSpecificationsInDomainLayer` | `*Specification` classes outside infrastructure must live in `domain.specification` |
| 13 | `jpaSpecificationsInInfrastructure` | `*JpaSpecification` classes must live in `infrastructure` |
| 14 | `compositionOverInheritance` | Aggregates, entities, and value objects must not extend another domain class — enforces composition-over-inheritance in the domain model |
| 15 | `serviceNaming` | Non-interface classes in `application.service` must end with `Service` |
| 16 | `controllerNaming` | Classes in `webapi.controller` must end with `Controller` |
| 17 | `repositoryNaming` | Classes in `domain.repository` must end with `Repository` |
| 18 | `eventHandlerNaming` | Classes in `application.eventhandler` must end with `EventHandler` |
| 19 | `specificationNaming` | Classes in `domain.specification` must end with `Specification` |
| 20 | `domainExceptionsLocation` | Classes assignable to `DomainException` must live in `domain.exception` — `application.exception` is explicitly excluded (see [ADR-0009](0009-building-not-found-in-application.md)) |
| 21 | `noCyclesBetweenLayers` | No circular dependencies between the four layer slices |

### Deliberate exceptions in the layer rule (rule 1)

| Exception | Reason |
|---|---|
| `webapi` → `domain.shared` | `DeviceType`, `EnergyUnit`, `ErrorCode` are shared-kernel enums used at every layer boundary |
| `webapi.exception` → `domain.exception` | `GlobalExceptionHandler` must reference domain exception types to match them with `@ExceptionHandler` |
| `webapi.websocket` → `domain.event` | `BuildingWebSocketEventHandler` subscribes to domain events — see [ADR-0007](0007-websocket-handler-in-webapi.md) |
| `webapi.websocket` → `domain.valueobject` | Same handler reads `Energy` value object fields to build the WebSocket push payload |

## Consequences

**Positive:**
- Layer violations are caught on every push, not in code review
- New contributors get a machine-readable, always-current statement of architectural rules
- ArchUnit runs in milliseconds — no performance cost to the CI pipeline
- The comment block in `DddArchitectureTest.java` also documents what ArchUnit *cannot* enforce (runtime/behavioural rules), making the test file itself a living architecture document

**Negative:**
- Some legitimate cross-layer patterns require explicit `ignoreDependency` exceptions that must be maintained as the code evolves
- Rules must be updated whenever the architecture intentionally changes — a forgotten update causes a false-positive test failure
