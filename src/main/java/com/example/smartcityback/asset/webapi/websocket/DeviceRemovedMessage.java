package com.example.smartcityback.asset.webapi.websocket;

import com.example.smartcityback.asset.domain.shared.enums.DeviceType;

import java.util.UUID;

/**
 * WebSocket push message — sent to /topic/buildings/{buildingId}/devices/removed
 * when a device is removed from a building.
 */
public record DeviceRemovedMessage(UUID buildingId, UUID deviceId, DeviceType deviceType) {}
