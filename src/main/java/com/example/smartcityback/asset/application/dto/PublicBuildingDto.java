package com.example.smartcityback.asset.application.dto;

import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Application-layer read model for a public building.
 * Returned by PublicBuildingQueryService — contains no web or infrastructure types.
 */
public record PublicBuildingDto(
        UUID id,
        String name,
        String location,
        BigDecimal consumptionValue,
        EnergyUnit consumptionUnit,
        List<EnergyDeviceDto> devices
) {}
