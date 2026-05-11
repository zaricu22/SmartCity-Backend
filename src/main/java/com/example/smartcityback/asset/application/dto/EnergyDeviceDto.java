package com.example.smartcityback.asset.application.dto;

import com.example.smartcityback.asset.domain.shared.enums.DeviceType;
import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Application-layer read model for a single energy device.
 * Returned by PublicBuildingQueryService — contains no web or infrastructure types.
 */
public record EnergyDeviceDto(
        UUID id,
        DeviceType type,
        BigDecimal ratedCapacityValue,
        EnergyUnit ratedCapacityUnit,
        BigDecimal productionRateValue,
        EnergyUnit productionRateUnit
) {}
