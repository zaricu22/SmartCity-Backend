package com.example.smartcityback.asset.application.command;

import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;

import java.math.BigDecimal;


public record ChangeProductionCommand(
    BigDecimal productionValue,
    EnergyUnit productionUnit
) {}

