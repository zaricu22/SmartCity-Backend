package com.example.smartcityback.asset.webapi.websocket;

import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * WebSocket push message — sent to /topic/buildings/{buildingId}/production
 * when a device's production rate changes.
 */
public record ProductionUpdateMessage(
        UUID buildingId,
        UUID deviceId,
        BigDecimal oldValue,
        EnergyUnit oldUnit,
        BigDecimal newValue,
        EnergyUnit newUnit
) {}
