package com.example.smartcityback.asset.domain.entity;

import com.example.smartcityback.asset.domain.exception.DeviceCapacityLimitException;
import com.example.smartcityback.asset.domain.exception.ValidationException;
import com.example.smartcityback.asset.domain.shared.enums.DeviceType;
import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;
import com.example.smartcityback.asset.domain.valueobject.Energy;
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("creates a solar device with a 100 kW rated capacity and a production rate that starts at zero")
    void create_validTypeAndCapacity_succeeds() {
        EnergyDevice device = new EnergyDevice(DEVICE_ID, "Test Device", DeviceType.SOLAR, CAPACITY_100_KW);

        assertThat(device.getId()).isEqualTo(DEVICE_ID);
        assertThat(device.getName()).isEqualTo("Test Device");
        assertThat(device.getType()).isEqualTo(DeviceType.SOLAR);
        assertThat(device.getDeviceRatedCapacity()).isEqualTo(CAPACITY_100_KW);
        assertThat(device.getProductionRate().value()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(device.getProductionRate().unit()).isEqualTo(EnergyUnit.kW);
    }

    @Test
    @DisplayName("accepts every device type — solar, pump, and battery — not just the common one")
    void create_allDeviceTypes_succeed() {
        assertThatCode(() -> new EnergyDevice(DEVICE_ID, "Test Device", DeviceType.SOLAR, CAPACITY_100_KW)).doesNotThrowAnyException();
        assertThatCode(() -> new EnergyDevice(DEVICE_ID, "Test Device", DeviceType.PUMP, CAPACITY_100_KW)).doesNotThrowAnyException();
        assertThatCode(() -> new EnergyDevice(DEVICE_ID, "Test Device", DeviceType.BATTERY, CAPACITY_100_KW)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejects creating a device with no name")
    void create_nullName_throwsValidationException() {
        assertThatThrownBy(() -> new EnergyDevice(DEVICE_ID, null, DeviceType.SOLAR, CAPACITY_100_KW))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("rejects creating a device whose name is an empty string")
    void create_emptyName_throwsValidationException() {
        assertThatThrownBy(() -> new EnergyDevice(DEVICE_ID, "", DeviceType.SOLAR, CAPACITY_100_KW))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("rejects creating a device with no device type")
    void create_nullType_throwsValidationException() {
        assertThatThrownBy(() -> new EnergyDevice(DEVICE_ID, "Test Device", null, CAPACITY_100_KW))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("rejects creating a device with no rated capacity")
    void create_nullCapacity_throwsValidationException() {
        assertThatThrownBy(() -> new EnergyDevice(DEVICE_ID, "Test Device", DeviceType.SOLAR, null))
                .isInstanceOf(ValidationException.class);
    }

    // =====================================================================
    // changeProductionRate
    //
    // if (newProductionRate.greaterThan(deviceRatedCapacity))
    // =====================================================================

    @Test
    @DisplayName("allows setting production below the device's rated capacity")
    void changeProductionRate_withinCapacity_succeeds() {
        EnergyDevice device   = new EnergyDevice(DEVICE_ID, "Test Device", DeviceType.SOLAR, CAPACITY_100_KW);
        Energy production80kW = new Energy(new BigDecimal("80"), EnergyUnit.kW);

        device.changeProduction(production80kW);

        assertThat(device.getProductionRate()).isEqualTo(production80kW);
    }

    @Test
    @DisplayName("allows setting production exactly at the device's rated capacity, not just strictly below it")
    void changeProductionRate_equalToCapacity_succeeds() {
        EnergyDevice device = new EnergyDevice(DEVICE_ID, "Test Device", DeviceType.SOLAR, CAPACITY_100_KW);

        device.changeProduction(CAPACITY_100_KW);

        assertThat(device.getProductionRate()).isEqualTo(CAPACITY_100_KW);
    }

    @Test
    @DisplayName("rejects setting production above the device's rated capacity")
    void changeProductionRate_exceedsCapacity_throwsDeviceCapacityLimitException() {
        EnergyDevice device      = new EnergyDevice(DEVICE_ID, "Test Device", DeviceType.SOLAR, CAPACITY_100_KW);
        Energy overCapacity101kW = new Energy(new BigDecimal("101"), EnergyUnit.kW);

        assertThatThrownBy(() -> device.changeProduction(overCapacity101kW))
                .isInstanceOf(DeviceCapacityLimitException.class);
    }

    @Test
    @DisplayName("allows 999 kW of production on a device rated for 1 MW — proves the capacity check converts "
            + "units instead of comparing the raw numbers 999 and 1")
    void changeProductionRate_crossUnit_999kWWithin1MWCapacity_succeeds() {
        // 1 MW = 1000 kW; 999 kW < 1 MW — must NOT throw.
        Energy capacity1MW  = new Energy(new BigDecimal("1"), EnergyUnit.MW);
        EnergyDevice device = new EnergyDevice(DEVICE_ID, "Test Device", DeviceType.SOLAR, capacity1MW);
        Energy production   = new Energy(new BigDecimal("999"), EnergyUnit.kW);

        assertThatCode(() -> device.changeProduction(production)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejects 2 MW of production on a device rated for only 1 MW, even though both values are "
            + "already expressed in the same unit")
    void changeProductionRate_crossUnit_2MWExceeds1MWCapacity_throws() {
        Energy capacity1MW  = new Energy(new BigDecimal("1"), EnergyUnit.MW);
        EnergyDevice device = new EnergyDevice(DEVICE_ID, "Test Device", DeviceType.SOLAR, capacity1MW);
        Energy production   = new Energy(new BigDecimal("2"), EnergyUnit.MW);

        assertThatThrownBy(() -> device.changeProduction(production))
                .isInstanceOf(DeviceCapacityLimitException.class);
    }

    // =====================================================================
    // Equality / hashCode - because on ID so no need to test them separately
    // =====================================================================
}
