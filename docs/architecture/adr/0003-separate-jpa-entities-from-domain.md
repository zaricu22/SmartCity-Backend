# ADR-0003: Separate JPA Entities from Domain Entities

**Status:** Accepted  
**Date:** 2026-05-04

## Context

JPA requires `@Entity`, `@Table`, `@Column`, `@ManyToOne` and similar annotations
to work. Placing these on domain aggregates and entities pollutes the domain layer
with framework concerns and prevents keeping domain classes as pure Java objects.

Alternatives considered:
- **Annotate domain objects directly** — simpler, fewer files, but domain layer depends on JPA
- **Use `@Embeddable` on value objects only** — partial separation, domain aggregate still annotated
- **Full separation** — domain objects are pure Java; separate JPA entity classes in infrastructure

## Decision

**Full separation**: every domain concept has a corresponding JPA entity in
`infrastructure.persistence.entity`.

| Domain | JPA Entity |
|---|---|
| `domain.aggregate.PublicBuilding` | `infrastructure.persistence.entity.PublicBuildingJpaEntity` |
| `domain.entity.EnergyDevice` | `infrastructure.persistence.entity.EnergyDeviceJpaEntity` |
| `domain.valueobject.Energy` | `infrastructure.persistence.embedded.EnergyEmbeddable` |

`Energy` (value object) is stored as `@Embeddable` — value + unit in two columns —
because it is always embedded inside a JPA entity, never an independent row.

Three mapper classes in `infrastructure.persistence.mapper` convert between domain and JPA
representations:

| Mapper | Responsibility |
|---|---|
| `PublicBuildingMapper` | `toDomain()` reconstitutes the aggregate from JPA; `toJpa()` creates a new JPA entity tree; `updateJpaEntity()` mutates an existing JPA entity in-place for UPDATE operations — required because of `orphanRemoval = true` on the device collection (Hibernate does not allow replacing the list reference directly) |
| `EnergyDeviceMapper` | `toDomain()` reconstitutes an `EnergyDevice` entity; `toJpa()` takes the parent `PublicBuildingJpaEntity` as a parameter to set the `@ManyToOne` back-reference (`entity.setBuilding(building)`) — required for JPA owning-side consistency |
| `EnergyMapper` | Converts `Energy ↔ EnergyEmbeddable`; declared as a utility but currently unused — both `PublicBuildingMapper` and `EnergyDeviceMapper` inline the conversion directly. If conversion logic becomes non-trivial, `EnergyMapper` should be wired in |

## Consequences

**Positive:**
- Domain layer has zero JPA annotations — fully portable, testable without a database
- JPA schema changes (column renames, index additions) do not touch domain classes
- ArchUnit enforces `@Entity` only in infrastructure — no accidental leakage (rule 4 in ADR-0002)

**Negative:**
- Three mapper classes required, each with both `toDomain()` and `toJpa()` directions
- Each persistable domain object requires a separate `reconstitute()` static factory to avoid re-running validation and generating spurious domain events on load — see [ADR-0013](0013-reconstitution-via-domain-methods.md)
- More files to maintain when the domain model changes — a field added to the aggregate requires changes in the domain class, the JPA entity, and the mapper
