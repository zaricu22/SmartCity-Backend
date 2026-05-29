package com.example.smartcityback.asset.application.service;

import com.example.smartcityback.asset.application.dto.PublicBuildingDto;
import com.example.smartcityback.asset.application.exception.BuildingNotFoundException;
import com.example.smartcityback.asset.domain.aggregate.PublicBuilding;
import com.example.smartcityback.asset.domain.entity.EnergyDevice;
import com.example.smartcityback.asset.domain.repository.PublicBuildingRepository;
import com.example.smartcityback.asset.domain.shared.enums.DeviceType;
import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;
import com.example.smartcityback.asset.domain.valueobject.Energy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class PublicBuildingQueryServiceTest {

    @Mock
    private PublicBuildingRepository repository;

    @InjectMocks
    private PublicBuildingQueryService queryService;

    private static final UUID BUILDING_ID = UUID.randomUUID();
    private static final UUID DEVICE_ID   = UUID.randomUUID();

    // =====================================================================
    // Helpers
    // =====================================================================

    private PublicBuilding buildingEntity(List<EnergyDevice> devices) {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        devices.forEach(building::addDevice);
        if (!devices.isEmpty()) {
            building.changeConsumption(new Energy(new BigDecimal("50"), EnergyUnit.kW));
        }
        return building;
    }

    private EnergyDevice deviceEntity() {
        EnergyDevice device = new EnergyDevice(DEVICE_ID, DeviceType.SOLAR,
                new Energy(new BigDecimal("100"), EnergyUnit.kW));
        device.changeProduction(new Energy(new BigDecimal("60"), EnergyUnit.kW));
        return device;
    }

    // =====================================================================
    // getById — found
    // =====================================================================

    @Test
    void getById_buildingWithNoDevices_mapsAllScalarFields() {
        given(repository.findById(BUILDING_ID))
                .willReturn(Optional.of(buildingEntity(List.of())));

        PublicBuildingDto response = queryService.getById(BUILDING_ID);

        assertThat(response.id()).isEqualTo(BUILDING_ID);
        assertThat(response.name()).isEqualTo("City Hall");
        assertThat(response.location()).isEqualTo("Main St 1");
        assertThat(response.consumptionValue()).isEqualByComparingTo("0");
        assertThat(response.consumptionUnit()).isEqualTo(EnergyUnit.kW);
        assertThat(response.devices()).isEmpty();
    }

    @Test
    void getById_buildingWithDevices_mapsDeviceList() {
        given(repository.findById(BUILDING_ID))
                .willReturn(Optional.of(buildingEntity(List.of(deviceEntity()))));

        PublicBuildingDto response = queryService.getById(BUILDING_ID);

        assertThat(response.devices()).hasSize(1);
        assertThat(response.devices().get(0).id()).isEqualTo(DEVICE_ID);
        assertThat(response.devices().get(0).type()).isEqualTo(DeviceType.SOLAR);
        assertThat(response.devices().get(0).ratedCapacityValue()).isEqualByComparingTo("100");
        assertThat(response.devices().get(0).ratedCapacityUnit()).isEqualTo(EnergyUnit.kW);
    }

    @Test
    void getById_multipleDevices_allMapped() {
        EnergyDevice solar   = deviceEntity();
        EnergyDevice battery = new EnergyDevice(UUID.randomUUID(), DeviceType.BATTERY,
                new Energy(new BigDecimal("200"), EnergyUnit.kW));

        given(repository.findById(BUILDING_ID))
                .willReturn(Optional.of(buildingEntity(List.of(solar, battery))));

        PublicBuildingDto response = queryService.getById(BUILDING_ID);

        assertThat(response.devices()).hasSize(2);
        assertThat(response.devices())
                .extracting(d -> d.type())
                .containsExactlyInAnyOrder(DeviceType.SOLAR, DeviceType.BATTERY);
    }

    @Test
    void getById_consumptionZero_mapsCorrectly() {
        PublicBuilding entity = new PublicBuilding(BUILDING_ID, "Library", "Park Ave 5");
        entity.changeConsumption(new Energy(BigDecimal.ZERO, EnergyUnit.MW));
        given(repository.findById(BUILDING_ID)).willReturn(Optional.of(entity));

        PublicBuildingDto response = queryService.getById(BUILDING_ID);

        assertThat(response.consumptionValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.consumptionUnit()).isEqualTo(EnergyUnit.MW);
    }

    // =====================================================================
    // getById — not found
    // =====================================================================

    @Test
    void getById_buildingNotFound_throwsBuildingNotFoundException() {
        given(repository.findById(BUILDING_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.getById(BUILDING_ID))
                .isInstanceOf(BuildingNotFoundException.class);
    }

    // =====================================================================
    // Isolation: repository is called exactly once per getById invocation
    // =====================================================================

    @Test
    void getById_callsRepositoryExactlyOnce() {
        given(repository.findById(BUILDING_ID))
                .willReturn(Optional.of(buildingEntity(List.of())));

        queryService.getById(BUILDING_ID);

        then(repository).should(times(1)).findById(BUILDING_ID);
        then(repository).shouldHaveNoMoreInteractions();
    }
}
