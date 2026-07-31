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
domainEvents.add(new DeviceAddedEvent(id, newDevice.getId(), newDevice.getName(), newDevice.getType()));

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
PublicBuilding building = repository.findById(buildingId).orElseThrow(BuildingNotFoundException::new);
repository.delete(buildingId);
eventPublisher.publishEvent(new BuildingDeletedEvent(buildingId, building.getName()));
```

Note the building is loaded *before* `delete()` — the event carries the
building's name for downstream consumers (audit log, WebSocket push), which
wouldn't be available after the row is gone.

**Reason:** the `pullEvents()` flow assumes an aggregate instance that gets
mutated and saved, then has its events collected. Deletion doesn't fit that
shape — there's no invariant for a domain method to protect, and the
aggregate ceases to exist rather than being saved. `DeviceRemovedEvent`
(removing a device from a still-existing building) *is* raised through the
normal aggregate path via `PublicBuilding.removeDevice()`, same as every
other mutation — only whole-aggregate deletion is the exception, not "delete"
operations in general.

## Amendment — 2026-08-06: listeners must use `@TransactionalEventListener(AFTER_COMMIT)`

**Status:** Correction

The "Positive" consequence above claimed "event publishing is fire-and-forget
**after commit**" — that was aspirational, not actual. `AppService` methods
are `@Transactional`, and `eventPublisher.publishEvent(...)` is called from
inside that same transactional method, right after `repository.save(...)`.
Plain `@EventListener` (used by both `BuildingWebSocketEventHandler` and
`AuditLogEventHandler`) fires **synchronously at publish time**, before the
surrounding transaction commits — not after.

For the WebSocket handler this broke client-visible correctness: a client
receiving the push and immediately refetching (`GET /v1/buildings/{id}`)
could race the backend's own commit and read pre-write state, since its own
read ran in a separate transaction that started before the write transaction
had committed. For the audit-log handler the symptom was quieter but the
same bug: an exception thrown from *any* listener during publish — audit log
included — propagates back through `publishEvent()` and, because it runs
before commit, could roll back the aggregate save itself, contradicting the
"handler failures are logged but do not roll back the aggregate change"
claim in the Negative consequences below. A successful audit log line was
also no guarantee the write it described actually committed.

**Fix:** every handler in both `BuildingWebSocketEventHandler` and
`AuditLogEventHandler` now uses
`@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` instead
of `@EventListener`. This defers execution until
`TransactionSynchronizationManager` confirms the transaction actually
committed — still synchronous, still same thread, still before the
`AppService` method returns to the controller, but now strictly after the
write is durable. Any client reacting to the WebSocket push is guaranteed to
see committed data on a subsequent read, and every audit log line now
describes a change that is genuinely persisted.

This does **not** change the "Negative" consequence about slow handlers
blocking the request thread — `AFTER_COMMIT` still runs inline as part of
commit, just later in that same sequence.
