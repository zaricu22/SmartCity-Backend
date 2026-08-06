package com.example.smartcityback.asset.webapi.request;

import com.example.smartcityback.asset.domain.shared.enums.DeviceType;
import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

@Schema
public record AddDeviceRequest(
        @NotBlank String name,
        @NotNull DeviceType type,
        @Positive BigDecimal ratedCapacityValue,
        @NotNull EnergyUnit ratedCapacityUnit,
        @NotNull Long version
) {}
