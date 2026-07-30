package com.example.smartcityback.asset.domain.specification;

import com.example.smartcityback.asset.domain.aggregate.PublicBuilding;
import com.example.smartcityback.asset.domain.entity.EnergyDevice;
import com.example.smartcityback.asset.domain.shared.enums.DeviceType;
import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;
import com.example.smartcityback.asset.domain.valueobject.Energy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class SubsidyEligibilitySpecificationTest {

    private final SubsidyEligibilitySpecification spec = new SubsidyEligibilitySpecification();

    private static final Energy CAPACITY_100_KW = new Energy(new BigDecimal("100"), EnergyUnit.kW);

    private PublicBuilding buildingWithDevices(int deviceCount, BigDecimal consumptionValue) {
        PublicBuilding building = new PublicBuilding(UUID.randomUUID(), "City Hall", "Main St 1");
        for (int i = 0; i < deviceCount; i++) {
            building.addDevice(new EnergyDevice(UUID.randomUUID(), DeviceType.SOLAR, CAPACITY_100_KW));
        }
        building.changeConsumption(new Energy(consumptionValue, EnergyUnit.kW));
        return building;
    }

    @Test
    void isSatisfiedBy_twoDevicesAndConsumptionAbove50_returnsTrue() {
        PublicBuilding building = buildingWithDevices(2, new BigDecimal("50.01"));

        assertThat(spec.isSatisfiedBy(building)).isTrue();
    }

    @Test
    void isSatisfiedBy_moreThanTwoDevices_returnsTrue() {
        PublicBuilding building = buildingWithDevices(3, new BigDecimal("100"));

        assertThat(spec.isSatisfiedBy(building)).isTrue();
    }

    @Test
    void isSatisfiedBy_fewerThanTwoDevices_returnsFalse() {
        PublicBuilding building = buildingWithDevices(1, new BigDecimal("100"));

        assertThat(spec.isSatisfiedBy(building)).isFalse();
    }

    @Test
    void isSatisfiedBy_noDevices_returnsFalse() {
        // 0 devices means 0 total capacity, so consumption can only be 0 without
        // tripping BuildingTotalCapacityExceededException.
        PublicBuilding building = buildingWithDevices(0, BigDecimal.ZERO);

        assertThat(spec.isSatisfiedBy(building)).isFalse();
    }

    @Test
    void isSatisfiedBy_consumptionExactly50_returnsFalse() {
        // rule is strictly greater-than 50, not >=
        PublicBuilding building = buildingWithDevices(2, new BigDecimal("50"));

        assertThat(spec.isSatisfiedBy(building)).isFalse();
    }

    @Test
    void isSatisfiedBy_consumptionBelow50_returnsFalse() {
        PublicBuilding building = buildingWithDevices(2, new BigDecimal("49.99"));

        assertThat(spec.isSatisfiedBy(building)).isFalse();
    }

    @Test
    void isSatisfiedBy_devicesOkButConsumptionTooLow_returnsFalse() {
        PublicBuilding building = buildingWithDevices(5, BigDecimal.ZERO);

        assertThat(spec.isSatisfiedBy(building)).isFalse();
    }
}
