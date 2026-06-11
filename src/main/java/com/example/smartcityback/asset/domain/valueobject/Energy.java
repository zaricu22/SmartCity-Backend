package com.example.smartcityback.asset.domain.valueobject;

import com.example.smartcityback.asset.domain.exception.ValidationException;
import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;
import com.example.smartcityback.asset.domain.shared.enums.ErrorCode;

import java.math.BigDecimal;

// This common class is enough for all kinds of energy (consumption, production, capacity, etc.),
// for potential further expansions we can make wrapper classes

public class Energy {

    private final BigDecimal value;
    private final EnergyUnit unit;

    // Private constructor for reconstitution from persistence — bypasses validation because
    // data from the DB is already valid (was validated on write). Final fields require a
    // constructor overload since they cannot be assigned outside a constructor.
    private Energy(BigDecimal value, EnergyUnit unit, boolean fromPersistence) {
        this.value = value;
        this.unit = unit;
    }

    public static Energy reconstitute(BigDecimal value, EnergyUnit unit) {
        return new Energy(value, unit, true);
    }

    public Energy(BigDecimal value, EnergyUnit unit) {
        if (value == null) {
            throw new ValidationException("Energy must have a value!", ErrorCode.ENERGY_VALUE_REQURIED);
        }

        if (unit == null) {
            throw new ValidationException("Energy must have a unit!", ErrorCode.ENERGY_UNIT_REQUIRED);
        }

        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Energy value cannot be negative!", ErrorCode.ENERGY_NEGATIVE);
        }

        this.value = value;
        this.unit = unit;
    }

    // Immutable getters
    public BigDecimal value() {
        return value;
    }
    public EnergyUnit unit() {
        return unit;
    }

    public Energy to(EnergyUnit targetUnit) {

        if (this.unit == targetUnit)
            return this;

        BigDecimal kw = unit.toKW(value);
        BigDecimal converted = targetUnit.fromKW(kw);

        return new Energy(converted, targetUnit);
    }

    public boolean greaterThan(Energy other) {
        return compareTo(other) > 0;
    }

    public boolean lessThan(Energy other) {
        return compareTo(other) < 0;
    }

    public int compareTo(Energy other) {

        BigDecimal thisKw = unit.toKW(value);
        BigDecimal otherKw = other.unit.toKW(other.value);

        return thisKw.compareTo(otherKw);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Energy energy)) return false;

        return compareTo(energy) == 0;
    }

    @Override
    public int hashCode() {
        return unit.toKW(value).hashCode();
    }
}
