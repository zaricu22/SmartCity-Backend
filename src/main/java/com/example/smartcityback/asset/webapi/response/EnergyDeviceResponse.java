package com.example.smartcityback.asset.webapi.response;

import com.example.smartcityback.asset.domain.shared.enums.DeviceType;
import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;

import java.math.BigDecimal;
import java.util.UUID;

public record EnergyDeviceResponse(
        UUID id,
        DeviceType type,
        BigDecimal ratedCapacityValue,
        EnergyUnit ratedCapacityUnit,
        BigDecimal productionRateValue,
        EnergyUnit productionRateUnit
) {}
