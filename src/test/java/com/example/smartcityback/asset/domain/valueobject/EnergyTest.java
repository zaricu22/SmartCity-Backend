package com.example.smartcityback.asset.domain.valueobject;

import com.example.smartcityback.asset.domain.exception.ValidationException;
import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class EnergyTest {

    // =====================================================================
    // Construction / Validation
    // =====================================================================

    @Test
    @DisplayName("creates a 100 kW energy value with the amount and unit preserved as given")
    void create_validValueAndUnit_succeeds() {
        Energy energy = new Energy(new BigDecimal("100"), EnergyUnit.kW);

        assertThat(energy.value()).isEqualByComparingTo("100");
        assertThat(energy.unit()).isEqualTo(EnergyUnit.kW);
    }

    @Test
    @DisplayName("allows a zero-value energy amount")
    void create_zeroValue_succeeds() {
        Energy energy = new Energy(BigDecimal.ZERO, EnergyUnit.kW);

        assertThat(energy.value()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("rejects an energy value with no numeric amount")
    void create_nullValue_throwsValidationException() {
        assertThatThrownBy(() -> new Energy(null, EnergyUnit.kW))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("rejects an energy value with no unit")
    void create_nullUnit_throwsValidationException() {
        assertThatThrownBy(() -> new Energy(BigDecimal.TEN, null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("rejects a negative energy amount")
    void create_negativeValue_throwsValidationException() {
        assertThatThrownBy(() -> new Energy(new BigDecimal("-0.01"), EnergyUnit.kW))
                .isInstanceOf(ValidationException.class);
    }

    // =====================================================================
    // Unit conversion
    // =====================================================================

    @Test
    @DisplayName("converts 1000 kW to 1 MW")
    void to_kWToMW_convertsCorrectly() {
        Energy kw = new Energy(new BigDecimal("1000"), EnergyUnit.kW);

        Energy mw = kw.to(EnergyUnit.MW);

        assertThat(mw.value()).isEqualByComparingTo("1");
        assertThat(mw.unit()).isEqualTo(EnergyUnit.MW);
    }

    @Test
    @DisplayName("converts 1000 MW to 1 GW")
    void to_MWToGW_convertsCorrectly() {
        Energy mw = new Energy(new BigDecimal("1000"), EnergyUnit.MW);

        Energy gw = mw.to(EnergyUnit.GW);

        assertThat(gw.value()).isEqualByComparingTo("1");
        assertThat(gw.unit()).isEqualTo(EnergyUnit.GW);
    }

    @Test
    @DisplayName("converting kW to GW and back to kW returns the exact original value, with no precision lost "
            + "across the round trip")
    void to_kWToGW_roundTrip_isConsistent() {
        Energy original = new Energy(new BigDecimal("1000000"), EnergyUnit.kW);

        Energy gw = original.to(EnergyUnit.GW);
        Energy backToKw = gw.to(EnergyUnit.kW);

        assertThat(backToKw.value()).isEqualByComparingTo("1000000");
    }

    @Test
    @DisplayName("converting to the unit it's already in returns the exact same object, instead of a redundant copy")
    void to_sameUnit_returnsSameInstance() {
        Energy energy = new Energy(new BigDecimal("100"), EnergyUnit.kW);

        assertThat(energy.to(EnergyUnit.kW)).isSameAs(energy);
    }

    // =====================================================================
    // Comparison
    // =====================================================================

    @Test
    @DisplayName("10 kW compares as greater than 5 kW, and not the other way around")
    void greaterThan_largerValueSameUnit_returnsTrue() {
        Energy ten = new Energy(new BigDecimal("10"), EnergyUnit.kW);
        Energy five = new Energy(new BigDecimal("5"), EnergyUnit.kW);

        assertThat(ten.greaterThan(five)).isTrue();
        assertThat(five.greaterThan(ten)).isFalse();
    }

    @Test
    @DisplayName("1 MW compares as greater than 500 kW by converting both to a common unit first, not by "
            + "comparing the raw numbers 1 and 500")
    void greaterThan_acrossUnits_comparesInKW() {
        Energy oneMW  = new Energy(BigDecimal.ONE, EnergyUnit.MW);        // 1000 kW
        Energy fiveHundredKW = new Energy(new BigDecimal("500"), EnergyUnit.kW);

        assertThat(oneMW.greaterThan(fiveHundredKW)).isTrue();
        assertThat(fiveHundredKW.greaterThan(oneMW)).isFalse();
    }

    @Test
    @DisplayName("3 kW compares as less than 10 kW, and not the other way around")
    void lessThan_smallerValueSameUnit_returnsTrue() {
        Energy three = new Energy(new BigDecimal("3"), EnergyUnit.kW);
        Energy ten   = new Energy(new BigDecimal("10"), EnergyUnit.kW);

        assertThat(three.lessThan(ten)).isTrue();
        assertThat(ten.lessThan(three)).isFalse();
    }

    // =====================================================================
    // Equality / hashCode - because Energy is a Value Object, equality is not based on ID
    // =====================================================================

    @Test
    @DisplayName("two energy values with the same amount and the same unit are equal")
    void equals_sameValueSameUnit_returnsTrue() {
        Energy a = new Energy(new BigDecimal("50"), EnergyUnit.kW);
        Energy b = new Energy(new BigDecimal("50"), EnergyUnit.kW);

        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("1000 kW and 1 MW are equal, since they're the same physical quantity expressed in different units")
    void equals_equivalentValueDifferentUnits_returnsTrue() {
        Energy oneThousandKW = new Energy(new BigDecimal("1000"), EnergyUnit.kW);
        Energy oneMW         = new Energy(BigDecimal.ONE, EnergyUnit.MW);

        assertThat(oneThousandKW).isEqualTo(oneMW);
    }

    @Test
    @DisplayName("5 kW and 10 kW are not equal")
    void equals_differentValues_returnsFalse() {
        Energy five = new Energy(new BigDecimal("5"), EnergyUnit.kW);
        Energy ten  = new Energy(new BigDecimal("10"), EnergyUnit.kW);

        assertThat(five).isNotEqualTo(ten);
    }

    @Test
    @DisplayName("1000 kW and 1 MW produce the same hashCode, keeping the equals/hashCode contract intact for "
            + "values that equals() treats as the same")
    void hashCode_equivalentEnergies_areSame() {
        Energy a = new Energy(new BigDecimal("1000"), EnergyUnit.kW);
        Energy b = new Energy(BigDecimal.ONE, EnergyUnit.MW);

        assertThat(a.hashCode()).hasSameHashCodeAs(b.hashCode());
    }
}
