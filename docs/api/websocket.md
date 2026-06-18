# WebSocket API

The backend pushes real-time domain events to connected clients over STOMP/SockJS.
OpenAPI does not cover WebSocket — this document is the authoritative reference.

## Connection

**Endpoint:** `{baseUrl}/ws`  
**Protocol:** STOMP over SockJS (HTTP fallback for browsers without native WebSocket)

```js
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const client = new Client({
  webSocketFactory: () => new SockJS('http://localhost:8080/SmartCityREST/ws'),
  onConnect: () => {
    // subscribe to topics here
  },
});
client.activate();
```

Authentication is not required on the WebSocket connection itself — the same CORS
policy as the REST API applies (allowed origins: `http://localhost:4200`,
`https://zaricu22.github.io`).

## Topics

All topics are push-only (server → client). Clients subscribe but never send to these
destinations. The `{buildingId}` segment is the building's UUID.

---

### /topic/buildings/{buildingId}/consumption

Pushed when a building's energy consumption is updated via
`PUT /v1/buildings/{id}/consumption`.

**Message**
```json
{
  "buildingId": "a1000000-0000-0000-0000-000000000001",
  "oldValue": 150.0000,
  "oldUnit": "kW",
  "newValue": 320.0000,
  "newUnit": "kW"
}
```

| Field      | Type   | Description                        |
|------------|--------|------------------------------------|
| `buildingId` | UUID | The building that changed          |
| `oldValue` | number | Previous consumption value         |
| `oldUnit`  | string | Previous unit (`kW`, `MW`, `GW`)   |
| `newValue` | number | New consumption value              |
| `newUnit`  | string | New unit (`kW`, `MW`, `GW`)        |

**Subscription example**
```js
client.subscribe(`/topic/buildings/${buildingId}/consumption`, (frame) => {
  const msg = JSON.parse(frame.body);
  console.log(`Consumption updated: ${msg.newValue} ${msg.newUnit}`);
});
```

---

### /topic/buildings/{buildingId}/devices

Pushed when a new device is added to a building via
`POST /v1/buildings/{id}/devices`.

**Message**
```json
{
  "buildingId": "a1000000-0000-0000-0000-000000000001",
  "deviceId":   "d1000000-0001-0000-0000-000000000005",
  "deviceType": "SOLAR"
}
```

| Field        | Type   | Description                                   |
|--------------|--------|-----------------------------------------------|
| `buildingId` | UUID   | The building the device was added to          |
| `deviceId`   | UUID   | The newly created device                      |
| `deviceType` | string | `SOLAR`, `BATTERY`, or `PUMP`                 |

---

### /topic/buildings/{buildingId}/production

Pushed when a device's production rate is updated via
`PATCH /v1/buildings/{id}/devices/{deviceId}/production`.

**Message**
```json
{
  "buildingId": "a1000000-0000-0000-0000-000000000001",
  "deviceId":   "d1000000-0001-0000-0000-000000000001",
  "oldValue":   120.0000,
  "oldUnit":    "kW",
  "newValue":   160.0000,
  "newUnit":    "kW"
}
```

| Field        | Type   | Description                              |
|--------------|--------|------------------------------------------|
| `buildingId` | UUID   | The building that contains the device    |
| `deviceId`   | UUID   | The device whose production changed      |
| `oldValue`   | number | Previous production rate value           |
| `oldUnit`    | string | Previous unit (`kW`, `MW`, `GW`)         |
| `newValue`   | number | New production rate value                |
| `newUnit`    | string | New unit (`kW`, `MW`, `GW`)              |

---

## Event trigger map

| REST operation                  | WebSocket topic pushed            |
|---------------------------------|-----------------------------------|
| `PUT  /buildings/{id}/consumption`              | `.../consumption` |
| `POST /buildings/{id}/devices`                  | `.../devices`     |
| `PATCH /buildings/{id}/devices/{dId}/production`| `.../production`  |

## Notes

- Events are dispatched **synchronously** in the HTTP request thread — the WebSocket
  push completes before the REST response is returned to the caller.
- The in-memory broker (`/topic`) does not persist messages. Clients that are not
  connected when an event fires will miss it. For durable delivery, replace the
  simple broker with RabbitMQ or ActiveMQ in `WebSocketConfig`.
- There is no per-building access control on topic subscriptions — any authenticated
  WebSocket connection can subscribe to any building's topics.
