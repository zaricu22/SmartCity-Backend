# ADR-0007: WebSocket Handler in WebApi Layer

**Status:** Accepted  
**Date:** 2026-05-04

## Context

`BuildingWebSocketEventHandler` listens to domain events (`ConsumptionChangedEvent`,
`DeviceAddedEvent`) and pushes real-time messages to connected clients via
`SimpMessagingTemplate` (STOMP over WebSocket).

Two placement options:
- **`application.eventhandler`** — consistent with `AuditLogEventHandler`, keeps domain
  event handling in the application layer; but then `application` depends on
  `SimpMessagingTemplate`, which is a web/infrastructure concern
- **`webapi.websocket`** — handler depends on both domain events and web infrastructure;
  causes `webapi → domain.event` dependency, which violates the strict layer rule

## Decision

Place `BuildingWebSocketEventHandler` in **`webapi.websocket`**.

The handler's primary concern is translating domain events into WebSocket messages and
delivering them to clients — that is a presentation/delivery concern, not an application
orchestration concern. Keeping `SimpMessagingTemplate` (Spring WebSocket infrastructure)
out of the application layer is the higher priority. Note: `@EventListener` is a
`spring-context` annotation, not a web concern — it is also used in `AuditLogEventHandler`
in the application layer. The placement decision is driven by `SimpMessagingTemplate` alone.

The handler pushes two message types to connected clients:

| Domain event | WebSocket message | Topic |
|---|---|---|
| `ConsumptionChangedEvent` | `ConsumptionUpdateMessage` (old + new value/unit) | `/topic/buildings/{buildingId}/consumption` |
| `DeviceAddedEvent` | `DeviceAddedMessage` (buildingId, deviceId, deviceType) | `/topic/buildings/{buildingId}/devices` |

The `webapi → domain.valueobject` dependency exists because the handler reads `Energy.value()`
and `Energy.unit()` directly from the domain event payload to construct `ConsumptionUpdateMessage`
— there is no intermediate DTO or mapper between the domain event and the WebSocket message.

The `webapi → domain.event` and `webapi → domain.valueobject` dependencies are
explicitly allowed via `ignoreDependency` in `DddArchitectureTest`. See [ADR-0002](0002-archunit-ddd-enforcement.md).

## Consequences

**Positive:**
- Application layer stays free of web infrastructure (`SimpMessagingTemplate`)
- The handler's role is immediately clear from its package location
- Domain events remain pure — they have no knowledge of WebSocket

**Negative:**
- `DddArchitectureTest` requires an explicit exception in the layer dependency rule
- `webapi.websocket` now has a dependency on `domain.event` and `domain.valueobject`,
  which a strict reading of Onion Architecture does not permit

## Amendment — 2026-07-31: Handler extended to 6 events

`BuildingWebSocketEventHandler` now has six `@EventListener` methods, not two —
`ProductionChangedEvent`, `BuildingCreatedEvent`, `BuildingDeletedEvent`, and
`DeviceRemovedEvent` were added after this ADR's original table was written.
The original decision (placement in `webapi.websocket`, driven solely by the
`SimpMessagingTemplate` dependency) is unchanged and still governs all of them.

Two of the newer events use a different topic shape than the original table
shows — not a change in placement, just an extension of the pattern:

| Domain event | WebSocket message | Topic |
|---|---|---|
| `BuildingCreatedEvent` | `BuildingCreatedMessage` | `/topic/buildings` (collection-level — no id exists yet to subscribe per-building) |
| `BuildingDeletedEvent` | `BuildingDeletedMessage` | `/topic/buildings/deleted` (collection-level; kept separate from `/topic/buildings` so each topic carries one message shape) |
| `DeviceAddedEvent` | `DeviceAddedMessage` | `/topic/buildings/{buildingId}/devices` |
| `DeviceRemovedEvent` | `DeviceRemovedMessage` | `/topic/buildings/{buildingId}/devices/removed` |
| `ConsumptionChangedEvent` | `ConsumptionUpdateMessage` | `/topic/buildings/{buildingId}/consumption` |
| `ProductionChangedEvent` | `ProductionUpdateMessage` | `/topic/buildings/{buildingId}/production` |
