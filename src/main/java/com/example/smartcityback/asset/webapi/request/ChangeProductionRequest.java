package com.example.smartcityback.asset.webapi.request;

import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

@Schema
public record ChangeProductionRequest(
        @PositiveOrZero BigDecimal productionValue,
        EnergyUnit productionUnit,
        @NotNull Long version
) {}
