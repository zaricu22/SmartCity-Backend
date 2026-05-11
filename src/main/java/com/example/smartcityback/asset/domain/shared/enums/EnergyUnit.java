package com.example.smartcityback.asset.domain.shared.enums;

import java.math.BigDecimal;

// For bigger projects domain enums should not be used outside, every layer needs own enum and mapper (pragmatic DDD)
public enum EnergyUnit {
    kW(BigDecimal.ONE),  // default Domain choice - canonical unit
    MW(new BigDecimal("1000")),
    GW(new BigDecimal("1000000"));

    private final BigDecimal toKWFactor;

    EnergyUnit(BigDecimal toKWFactor) {
        this.toKWFactor = toKWFactor;
    }

    public BigDecimal toKW(BigDecimal unitValue) {
        return unitValue.multiply(toKWFactor);
    }

    public BigDecimal fromKW(BigDecimal valueKW) {
        return valueKW.divide(toKWFactor);
    }
}
