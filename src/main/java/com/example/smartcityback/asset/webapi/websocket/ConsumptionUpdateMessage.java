package com.example.smartcityback.asset.webapi.websocket;

import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * WebSocket push message — sent to /topic/buildings/{buildingId}/consumption
 * when a building's consumption changes.
 */
public record ConsumptionUpdateMessage(
        UUID buildingId,
        BigDecimal oldValue,
        EnergyUnit oldUnit,
        BigDecimal newValue,
        EnergyUnit newUnit
) {}
