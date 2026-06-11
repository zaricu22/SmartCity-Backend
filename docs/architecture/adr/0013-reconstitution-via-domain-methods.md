# ADR-0013: Reconstitution via Static Factory, Not Domain Methods

**Status:** Accepted  
**Date:** 2026-06-05

## Context

When loading a `PublicBuilding` from the database, the infrastructure mapper
(`PublicBuildingMapper.toDomain()`) must reconstruct the full aggregate from
JPA entities. The aggregate had no separate "reconstitute" constructor.

The previous implementation called domain methods during reconstitution:

```java
// old PublicBuildingMapper.toDomain()
building.changeConsumption(energy);               // re-runs capacity validation + generates event
jpaDevices.forEach(d -> building.addDevice(...)); // re-runs duplicate check + generates event
```

This caused a bug: `addDevice()` and `changeConsumption()` append domain events to
`domainEvents`. When the app service subsequently called `building.pullEvents()` after
a command, reconstitution events were published to WebSocket clients alongside the real
command event — spurious events on every write operation.

## Decision

Following DDD practice: the DB is the trusted source of truth and entities are validated
on write — reconstitution is pure hydration, not re-creation. Each persistable domain
object therefore should have a **`reconstitute()` static factory** that bypasses validation and
produces no side effects. Mappers use it exclusively for loading from DB.

| Class | Pattern | Reason |
|---|---|---|
| `PublicBuilding` | Private no-arg constructor + static factory | Fields are not `final` — factory assigns them directly after instantiation |
| `EnergyDevice` | Private 4-param constructor overload + static factory | `deviceRatedCapacity` is `final` — cannot be assigned outside a constructor |
| `Energy` | Private 2-param constructor overload + static factory | Both fields are `final` — cannot be assigned outside a constructor |

**Why bypass validation on reconstitution:**  
Data loaded from the DB was already validated when it was first persisted. Re-running
validation on trusted data is redundant overhead. More critically, calling domain methods
during reconstitution generates domain events — a fundamental misuse of the domain model.

**Precedence rule:** regular constructors remain unchanged and still enforce all invariants.
`reconstitute()` is only called by mappers in the infrastructure layer.

## Consequences

**Positive:**
- No domain events generated during reconstitution — spurious WebSocket publishes eliminated
- No validation re-run on trusted DB data — cleaner and faster load path
- Reconstitution and creation are explicitly separate code paths — intent is clear

**Negative:**
- If a domain invariant changes (e.g. a new mandatory field), existing DB records may
  fail to load until data is migrated — but this is true of any reconstitution approach
- `reconstitute()` bypasses validation — if DB data is somehow corrupt, it will not be
  caught on load. Accepted trade-off: the DB is a trusted source
