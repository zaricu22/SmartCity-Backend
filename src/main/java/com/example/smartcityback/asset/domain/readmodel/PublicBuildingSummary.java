package com.example.smartcityback.asset.domain.readmodel;

import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;

import java.math.BigDecimal;
import java.util.UUID;

public record PublicBuildingSummary(
        UUID id,
        String name,
        String location,
        BigDecimal consumptionValue,
        EnergyUnit consumptionUnit
) {}
