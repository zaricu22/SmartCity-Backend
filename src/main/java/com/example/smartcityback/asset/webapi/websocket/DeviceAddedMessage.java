package com.example.smartcityback.asset.webapi.websocket;

import com.example.smartcityback.asset.domain.shared.enums.DeviceType;

import java.util.UUID;

/**
 * WebSocket push message — sent to /topic/buildings/{buildingId}/devices
 * when a new device is added to a building.
 */
public record DeviceAddedMessage(
        UUID buildingId,
        UUID deviceId,
        String deviceName,
        DeviceType deviceType
) {}
