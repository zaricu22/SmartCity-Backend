package com.example.smartcityback.asset.webapi.websocket;

import java.util.UUID;

/**
 * WebSocket push message — sent to /topic/buildings/deleted when a building is deleted.
 *
 * Collection-level topic, same shape as /topic/buildings (creation) — kept as a separate
 * topic rather than reusing /topic/buildings so each topic carries exactly one message shape.
 */
public record BuildingDeletedMessage(UUID buildingId, String name) {}
