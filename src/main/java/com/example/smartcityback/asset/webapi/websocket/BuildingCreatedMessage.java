package com.example.smartcityback.asset.webapi.websocket;

import java.util.UUID;

/**
 * WebSocket push message — sent to /topic/buildings when a new building is created.
 *
 * Unlike the other WS messages in this package, this goes to a collection-level topic
 * rather than /topic/buildings/{buildingId}/... — no client can be subscribed to a
 * per-building topic for an id that didn't exist until this message is sent.
 */
public record BuildingCreatedMessage(
        UUID buildingId,
        String name,
        String location
) {}
