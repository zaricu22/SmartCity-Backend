package com.example.smartcityback.asset.domain.entity;

import com.example.smartcityback.asset.domain.exception.DeviceCapacityLimitException;
import com.example.smartcityback.asset.domain.exception.ValidationException;
import com.example.smartcityback.asset.domain.shared.enums.DeviceType;
import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;
import com.example.smartcityback.asset.domain.valueobject.Energy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class EnergyDeviceTest {

    private static final UUID   DEVICE_ID       = UUID.randomUUID();
    private static final Energy CAPACITY_100_KW = new Energy(new BigDecimal("100"), EnergyUnit.kW);

    // =====================================================================
    // Construction / Validation
    // =====================================================================

    @Test
    void create_validTypeAndCapacity_succeeds() {
        EnergyDevice device = new EnergyDevice(DEVICE_ID, DeviceType.SOLAR, CAPACITY_100_KW);

        assertThat(device.getId()).isEqualTo(DEVICE_ID);
        assertThat(device.getType()).isEqualTo(DeviceType.SOLAR);
        assertThat(device.getDeviceRatedCapacity()).isEqualTo(CAPACITY_100_KW);
        assertThat(device.getProductionRate().value()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(device.getProductionRate().unit()).isEqualTo(EnergyUnit.kW);
    }

    @Test
    void create_allDeviceTypes_succeed() {
        assertThatCode(() -> new EnergyDevice(DEVICE_ID, DeviceType.SOLAR,   CAPACITY_100_KW)).doesNotThrowAnyException();
        assertThatCode(() -> new EnergyDevice(DEVICE_ID, DeviceType.PUMP,    CAPACITY_100_KW)).doesNotThrowAnyException();
        assertThatCode(() -> new EnergyDevice(DEVICE_ID, DeviceType.BATTERY, CAPACITY_100_KW)).doesNotThrowAnyException();
    }

    @Test
    void create_nullType_throwsValidationException() {
        assertThatThrownBy(() -> new EnergyDevice(DEVICE_ID, null, CAPACITY_100_KW))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void create_nullCapacity_throwsValidationException() {
        assertThatThrownBy(() -> new EnergyDevice(DEVICE_ID, DeviceType.SOLAR, null))
                .isInstanceOf(ValidationException.class);
    }

    // =====================================================================
    // changeProductionRate
    //
    // if (newProductionRate.greaterThan(deviceRatedCapacity))
    // =====================================================================

    @Test
    void changeProductionRate_withinCapacity_succeeds() {
        EnergyDevice device   = new EnergyDevice(DEVICE_ID, DeviceType.SOLAR, CAPACITY_100_KW);
        Energy production80kW = new Energy(new BigDecimal("80"), EnergyUnit.kW);

        device.changeProduction(production80kW);

        assertThat(device.getProductionRate()).isEqualTo(production80kW);
    }

    @Test
    void changeProductionRate_equalToCapacity_succeeds() {
        EnergyDevice device = new EnergyDevice(DEVICE_ID, DeviceType.SOLAR, CAPACITY_100_KW);

        device.changeProduction(CAPACITY_100_KW);

        assertThat(device.getProductionRate()).isEqualTo(CAPACITY_100_KW);
    }

    @Test
    void changeProductionRate_exceedsCapacity_throwsDeviceCapacityLimitException() {
        EnergyDevice device      = new EnergyDevice(DEVICE_ID, DeviceType.SOLAR, CAPACITY_100_KW);
        Energy overCapacity101kW = new Energy(new BigDecimal("101"), EnergyUnit.kW);

        assertThatThrownBy(() -> device.changeProduction(overCapacity101kW))
                .isInstanceOf(DeviceCapacityLimitException.class);
    }

    @Test
    void changeProductionRate_crossUnit_999kWWithin1MWCapacity_succeeds() {
        // 1 MW = 1000 kW; 999 kW < 1 MW — must NOT throw.
        Energy capacity1MW  = new Energy(new BigDecimal("1"), EnergyUnit.MW);
        EnergyDevice device = new EnergyDevice(DEVICE_ID, DeviceType.SOLAR, capacity1MW);
        Energy production   = new Energy(new BigDecimal("999"), EnergyUnit.kW);

        assertThatCode(() -> device.changeProduction(production)).doesNotThrowAnyException();
    }

    @Test
    void changeProductionRate_crossUnit_2MWExceeds1MWCapacity_throws() {
        Energy capacity1MW  = new Energy(new BigDecimal("1"), EnergyUnit.MW);
        EnergyDevice device = new EnergyDevice(DEVICE_ID, DeviceType.SOLAR, capacity1MW);
        Energy production   = new Energy(new BigDecimal("2"), EnergyUnit.MW);

        assertThatThrownBy(() -> device.changeProduction(production))
                .isInstanceOf(DeviceCapacityLimitException.class);
    }

    // =====================================================================
    // Equality / hashCode - because on ID so no need to test them separately
    // =====================================================================
}
