package com.example.smartcityback.asset.application.mapper;

import com.example.smartcityback.asset.application.dto.PublicBuildingDto;
import com.example.smartcityback.asset.domain.aggregate.PublicBuilding;
import com.example.smartcityback.asset.domain.entity.EnergyDevice;
import com.example.smartcityback.asset.domain.shared.enums.DeviceType;
import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;
import com.example.smartcityback.asset.domain.valueobject.Energy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BuildingDtoMapperTest {

    private static final UUID BUILDING_ID = UUID.randomUUID();
    private static final UUID DEVICE_ID   = UUID.randomUUID();

    // =====================================================================
    // Scalar fields
    // =====================================================================

    @Test
    void toDto_mapsAllScalarFields() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");

        PublicBuildingDto dto = BuildingDtoMapper.toDto(building);

        assertThat(dto.id()).isEqualTo(BUILDING_ID);
        assertThat(dto.name()).isEqualTo("City Hall");
        assertThat(dto.location()).isEqualTo("Main St 1");
        assertThat(dto.consumptionValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dto.consumptionUnit()).isEqualTo(EnergyUnit.kW);
    }

    // =====================================================================
    // Device list
    // =====================================================================

    @Test
    void toDto_noDevices_returnsEmptyDeviceList() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");

        PublicBuildingDto dto = BuildingDtoMapper.toDto(building);

        assertThat(dto.devices()).isEmpty();
    }

    @Test
    void toDto_withDevice_mapsDeviceFields() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        building.addDevice(new EnergyDevice(DEVICE_ID, DeviceType.SOLAR,
                new Energy(new BigDecimal("100"), EnergyUnit.kW)));

        PublicBuildingDto dto = BuildingDtoMapper.toDto(building);

        assertThat(dto.devices()).hasSize(1);
        assertThat(dto.devices().get(0).id()).isEqualTo(DEVICE_ID);
        assertThat(dto.devices().get(0).type()).isEqualTo(DeviceType.SOLAR);
        assertThat(dto.devices().get(0).ratedCapacityValue()).isEqualByComparingTo("100");
        assertThat(dto.devices().get(0).ratedCapacityUnit()).isEqualTo(EnergyUnit.kW);
        assertThat(dto.devices().get(0).productionRateValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dto.devices().get(0).productionRateUnit()).isEqualTo(EnergyUnit.kW);
    }

    @Test
    void toDto_withMultipleDevices_mapsAll() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        building.addDevice(new EnergyDevice(UUID.randomUUID(), DeviceType.SOLAR,
                new Energy(new BigDecimal("100"), EnergyUnit.kW)));
        building.addDevice(new EnergyDevice(UUID.randomUUID(), DeviceType.BATTERY,
                new Energy(new BigDecimal("50"), EnergyUnit.kW)));

        PublicBuildingDto dto = BuildingDtoMapper.toDto(building);

        assertThat(dto.devices()).hasSize(2);
    }
}
