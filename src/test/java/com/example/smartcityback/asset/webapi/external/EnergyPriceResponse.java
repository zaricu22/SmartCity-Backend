package com.example.smartcityback.asset.webapi.external;

import java.math.BigDecimal;

public record EnergyPriceResponse(
        BigDecimal pricePerKWh,
        String currency
) {}
