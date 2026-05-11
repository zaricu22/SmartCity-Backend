package com.example.smartcityback.asset.webapi.response;

import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PublicBuildingResponse(
        UUID id,
        String name,
        String location,
        BigDecimal consumptionValue,
        EnergyUnit consumptionUnit,
        List<EnergyDeviceResponse> devices
) {}
