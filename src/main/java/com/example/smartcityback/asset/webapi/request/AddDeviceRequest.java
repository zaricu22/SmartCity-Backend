package com.example.smartcityback.asset.webapi.request;

import com.example.smartcityback.asset.domain.shared.enums.DeviceType;
import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record AddDeviceRequest(
        @NotNull DeviceType type,
        @Positive BigDecimal ratedCapacityValue,
        EnergyUnit ratedCapacityUnit
) {}
