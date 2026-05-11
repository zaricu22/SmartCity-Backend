package com.example.smartcityback.asset.application.command;

import com.example.smartcityback.asset.domain.shared.enums.DeviceType;
import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;

import java.math.BigDecimal;
import java.util.UUID;

public record AddDeviceCommand(
        UUID buildingId,
        DeviceType type,
        BigDecimal ratedCapacityValue,
        EnergyUnit ratedCapacityUnit
) {}
