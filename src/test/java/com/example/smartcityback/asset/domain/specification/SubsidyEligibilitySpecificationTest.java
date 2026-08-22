package com.example.smartcityback.asset.domain.specification;

import com.example.smartcityback.asset.domain.aggregate.PublicBuilding;
import com.example.smartcityback.asset.domain.entity.EnergyDevice;
import com.example.smartcityback.asset.domain.shared.enums.DeviceType;
import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;
import com.example.smartcityback.asset.domain.valueobject.Energy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class SubsidyEligibilitySpecificationTest {

    private final SubsidyEligibilitySpecification spec = new SubsidyEligibilitySpecification();

    private static final Energy CAPACITY_100_KW = new Energy(new BigDecimal("100"), EnergyUnit.kW);

    private PublicBuilding buildingWithDevices(int deviceCount, BigDecimal consumptionValue) {
        PublicBuilding building = new PublicBuilding(UUID.randomUUID(), "City Hall", "Main St 1");
        // Distinct names — addDevice() rejects a second device with the same name+type.
        for (int i = 0; i < deviceCount; i++) {
            building.addDevice(new EnergyDevice(UUID.randomUUID(), "Test Device " + i, DeviceType.SOLAR, CAPACITY_100_KW));
            building.getDevices().get(i).changeProduction(new Energy(new BigDecimal("100"), EnergyUnit.kW));
        }
        building.changeConsumption(new Energy(consumptionValue, EnergyUnit.kW));
        return building;
    }

    @Test
    @DisplayName("qualifies a building with 2 devices and consumption just above the 50 kW threshold (50.01 kW)")
    void isSatisfiedBy_twoDevicesAndConsumptionAbove50_returnsTrue() {
        PublicBuilding building = buildingWithDevices(2, new BigDecimal("50.01"));

        assertThat(spec.isSatisfiedBy(building)).isTrue();
    }

    @Test
    @DisplayName("qualifies a building with more than 2 devices, proving the device-count rule is 'at least 2', "
            + "not 'exactly 2'")
    void isSatisfiedBy_moreThanTwoDevices_returnsTrue() {
        PublicBuilding building = buildingWithDevices(3, new BigDecimal("100"));

        assertThat(spec.isSatisfiedBy(building)).isTrue();
    }

    @Test
    @DisplayName("disqualifies a building with only 1 device, even though its consumption is well above the "
            + "50 kW threshold")
    void isSatisfiedBy_fewerThanTwoDevices_returnsFalse() {
        PublicBuilding building = buildingWithDevices(1, new BigDecimal("100"));

        assertThat(spec.isSatisfiedBy(building)).isFalse();
    }

    @Test
    @DisplayName("disqualifies a building with zero devices — the fixture also has to use zero consumption here, "
            + "since a building with no devices has no production rate and can't legally record any consumption at all")
    void isSatisfiedBy_noDevices_returnsFalse() {
        // 0 devices means 0 total production rate, so consumption can only be 0 without
        // tripping BuildingProductionRateExceededException.
        PublicBuilding building = buildingWithDevices(0, BigDecimal.ZERO);

        assertThat(spec.isSatisfiedBy(building)).isFalse();
    }

    @Test
    @DisplayName("disqualifies a building whose consumption is exactly 50 kW — the rule is strictly greater "
            + "than 50, not 50 or above")
    void isSatisfiedBy_consumptionExactly50_returnsFalse() {
        // rule is strictly greater-than 50, not >=
        PublicBuilding building = buildingWithDevices(2, new BigDecimal("50"));

        assertThat(spec.isSatisfiedBy(building)).isFalse();
    }

    @Test
    @DisplayName("disqualifies a building whose consumption is just below the 50 kW threshold (49.99 kW)")
    void isSatisfiedBy_consumptionBelow50_returnsFalse() {
        PublicBuilding building = buildingWithDevices(2, new BigDecimal("49.99"));

        assertThat(spec.isSatisfiedBy(building)).isFalse();
    }

    @Test
    @DisplayName("disqualifies a building with 5 devices (enough) but zero consumption (not enough) — proves "
            + "both criteria are required together, not either one alone")
    void isSatisfiedBy_devicesOkButConsumptionTooLow_returnsFalse() {
        PublicBuilding building = buildingWithDevices(5, BigDecimal.ZERO);

        assertThat(spec.isSatisfiedBy(building)).isFalse();
    }
}
