package com.example.smartcityback.asset.webapi.request;

import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record ChangeProductionRequest(
        @PositiveOrZero BigDecimal productionValue,
        EnergyUnit productionUnit
) {}
