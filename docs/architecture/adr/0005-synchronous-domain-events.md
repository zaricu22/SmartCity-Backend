# ADR-0005: Synchronous Domain Events

**Status:** Accepted  
**Date:** 2026-05-04

## Context

After aggregate state changes (`addDevice`, `changeConsumption`), downstream components
need to react: the WebSocket handler pushes real-time updates to clients, and the
audit log handler records the operation. These reactions must happen reliably after
each command.

Alternatives considered:
- **Async messaging (Kafka, RabbitMQ)** — reliable delivery, retry, decoupled; requires
  broker infrastructure, distributed transaction concerns, harder local dev
- **Synchronous Spring events** — no infrastructure, runs in same thread and transaction,
  simple to test; no retry, handlers block the command response

## Decision

Use **Spring's `ApplicationEventPublisher`** synchronously. 
The Application Service calls `building.pullEvents()` after `repository.save()` and publishes each event
in the same thread:

```java
// PublicBuilding.addDevice(EnergyDevice newDevice) 
domainEvents.add(new DeviceAddedEvent(id, newDevice.getId(), newDevice.getType()));

// Application.addDevice(AddDeviceCommand cmd)
repository.save(building);
building.pullEvents().forEach(eventPublisher::publishEvent);
```

**Event ordering:** `save` before `pullEvents` is intentional. If an exception occurs
during save, events are never published. If save succeeds but a handler throws, the
transaction has already committed — handler failures are logged but do not roll back
the aggregate change.

**Only the aggregate root raises domain events.** `PublicBuilding` owns the `List<DomainEvent>`
and is the only class that adds to it. `EnergyDevice` (a domain entity inside the aggregate)
never raises events directly — in DDD, entities delegate state-change notifications upward to
the aggregate root, which decides what domain events to record. This keeps event responsibility
centralised and prevents handlers from reacting to partial or intermediate aggregate state.

`EnergyDevice.changeProduction()` itself raises no domain event — consistent with the
principle above, only the aggregate root does. `PublicBuilding.changeDeviceProduction()`
(the aggregate-level method that delegates to it) raises `ProductionChangedEvent`, and
`AppService.changeProduction()` does call `pullEvents()`, same as every other mutation.

Domain events implement the `DomainEvent` marker interface (`domain.event.DomainEvent`) so the
aggregate's internal list is typed as `List<DomainEvent>`. This gives compile-time safety:
only genuine domain events can be added to the list and dispatched via `ApplicationEventPublisher`.

## Consequences

**Positive:**
- No message broker required — simpler infrastructure, works out of the box locally
- Events are easy to test — handlers are plain Spring beans, no mock broker needed
- No distributed transaction problem: event publishing is fire-and-forget after commit

**Negative:**
- If a handler throws after save, the aggregate change is committed but the notification
  is lost — there is no retry mechanism
- All handlers run synchronously before the HTTP response returns — a slow handler
  blocks the request thread
- Not suitable if events need to cross service boundaries (different microservices)

## Amendment — 2026-07-31: `BuildingDeletedEvent` is a deliberate exception

**Status:** Superseded in part

"Only the aggregate root raises domain events" no longer holds without
exception. `BuildingDeletedEvent` is constructed and published directly in
`AppService.delete()`, bypassing `domainEvents`/`pullEvents()` entirely:

```java
// AppService.delete(UUID buildingId)
repository.delete(buildingId);
eventPublisher.publishEvent(new BuildingDeletedEvent(buildingId));
```

**Reason:** the `pullEvents()` flow assumes an aggregate instance that gets
mutated and saved, then has its events collected. Deletion doesn't fit that
shape — there's no invariant for a domain method to protect, and the
aggregate ceases to exist rather than being saved. `DeviceRemovedEvent`
(removing a device from a still-existing building) *is* raised through the
normal aggregate path via `PublicBuilding.removeDevice()`, same as every
other mutation — only whole-aggregate deletion is the exception, not "delete"
operations in general.
