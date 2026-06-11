package com.example.smartcityback.asset.application.command;

import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;

import java.math.BigDecimal;

// Commands are intentionally plain records with no guard clauses.
// Field-level validation (null checks, range constraints) is the responsibility of the HTTP
// layer via @Valid on the controller method. Business-rule validation is the responsibility
// of the domain aggregate. Commands are pure data carriers that move typed, named intent
// from the web layer to the application service — not the correct place for validation logic.
public record ChangeConsumptionCommand(
        BigDecimal consumptionValue,
        EnergyUnit consumptionUnit
) {}
