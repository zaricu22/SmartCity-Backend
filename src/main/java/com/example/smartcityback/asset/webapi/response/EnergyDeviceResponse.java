package com.example.smartcityback.asset.webapi.response;

import com.example.smartcityback.asset.domain.shared.enums.DeviceType;
import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema
public record EnergyDeviceResponse(
        UUID id,
        String name,
        DeviceType type,
        BigDecimal ratedCapacityValue,
        EnergyUnit ratedCapacityUnit,
        BigDecimal productionRateValue,
        EnergyUnit productionRateUnit
) {}
