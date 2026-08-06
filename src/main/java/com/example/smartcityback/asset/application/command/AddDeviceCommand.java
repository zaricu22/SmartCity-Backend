package com.example.smartcityback.asset.application.command;

import com.example.smartcityback.asset.domain.shared.enums.DeviceType;
import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;

import java.math.BigDecimal;
import java.util.UUID;

// Commands are intentionally plain records with no guard clauses.
// Field-level validation (null checks, range constraints) is the responsibility of the HTTP
// layer via @Valid on the controller method. Business-rule validation (e.g. capacity limits,
// duplicate device checks) is the responsibility of the domain aggregate. Commands are pure
// data carriers that move typed, named intent from the web layer to the application service —
// they are not the correct place for any validation logic.
public record AddDeviceCommand(
        UUID buildingId,
        String name,
        DeviceType type,
        BigDecimal ratedCapacityValue,
        EnergyUnit ratedCapacityUnit,
        Long version
) {}
