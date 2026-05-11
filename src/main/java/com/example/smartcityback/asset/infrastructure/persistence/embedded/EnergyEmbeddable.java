package com.example.smartcityback.asset.infrastructure.persistence.embedded;

import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.math.BigDecimal;

// Avoid usage of Domain ValueObject directly, make layers truly independent
@Embeddable
public class EnergyEmbeddable {
    private BigDecimal value;

    @Enumerated(EnumType.STRING)
    private EnergyUnit unit;

    protected EnergyEmbeddable() {}

    public EnergyEmbeddable(BigDecimal value, EnergyUnit unit) {
        this.value = value;
        this.unit = unit;
    }

    public BigDecimal getValue() {
        return value;
    }

    public EnergyUnit getUnit() {
        return unit;
    }
}
