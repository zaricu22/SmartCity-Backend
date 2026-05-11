package com.example.smartcityback.asset.webapi.mapper;

import com.example.smartcityback.asset.application.dto.EnergyDeviceDto;
import com.example.smartcityback.asset.application.dto.PublicBuildingDto;
import com.example.smartcityback.asset.domain.shared.enums.DeviceType;
import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;
import com.example.smartcityback.asset.webapi.response.PublicBuildingResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BuildingResponseMapperTest {

    private static final UUID BUILDING_ID = UUID.randomUUID();
    private static final UUID DEVICE_ID   = UUID.randomUUID();

    // =====================================================================
    // toResponse — scalar fields
    // =====================================================================

    @Test
    void toResponse_mapsAllScalarFields() {
        PublicBuildingDto dto = new PublicBuildingDto(
                BUILDING_ID, "City Hall", "Main St 1",
                new BigDecimal("50"), EnergyUnit.kW,
                List.of()
        );

        PublicBuildingResponse response = BuildingResponseMapper.toResponse(dto);

        assertThat(response.id()).isEqualTo(BUILDING_ID);
        assertThat(response.name()).isEqualTo("City Hall");
        assertThat(response.location()).isEqualTo("Main St 1");
        assertThat(response.consumptionValue()).isEqualByComparingTo("50");
        assertThat(response.consumptionUnit()).isEqualTo(EnergyUnit.kW);
    }

    @Test
    void toResponse_noDevices_returnsEmptyDeviceList() {
        PublicBuildingDto dto = new PublicBuildingDto(
                BUILDING_ID, "City Hall", "Main St 1",
                BigDecimal.ZERO, EnergyUnit.kW,
                List.of()
        );

        PublicBuildingResponse response = BuildingResponseMapper.toResponse(dto);

        assertThat(response.devices()).isEmpty();
    }

    @Test
    void toResponse_withDevice_mapsDeviceFields() {
        PublicBuildingDto dto = new PublicBuildingDto(
                BUILDING_ID, "City Hall", "Main St 1",
                BigDecimal.ZERO, EnergyUnit.kW,
                List.of(new EnergyDeviceDto(DEVICE_ID, DeviceType.SOLAR, new BigDecimal("100"), EnergyUnit.kW, new BigDecimal("60"), EnergyUnit.kW))
        );

        PublicBuildingResponse response = BuildingResponseMapper.toResponse(dto);

        assertThat(response.devices()).hasSize(1);
        assertThat(response.devices().get(0).id()).isEqualTo(DEVICE_ID);
        assertThat(response.devices().get(0).type()).isEqualTo(DeviceType.SOLAR);
        assertThat(response.devices().get(0).ratedCapacityValue()).isEqualByComparingTo("100");
        assertThat(response.devices().get(0).ratedCapacityUnit()).isEqualTo(EnergyUnit.kW);
        assertThat(response.devices().get(0).productionRateValue()).isEqualByComparingTo("60");
        assertThat(response.devices().get(0).productionRateUnit()).isEqualTo(EnergyUnit.kW);
    }

    // =====================================================================
    // toResponseList
    // =====================================================================

    @Test
    void toResponseList_emptyList_returnsEmptyList() {
        List<PublicBuildingResponse> responses = BuildingResponseMapper.toResponseList(List.of());

        assertThat(responses).isEmpty();
    }

    @Test
    void toResponseList_multipleBuildings_mapsAll() {
        List<PublicBuildingDto> dtos = List.of(
                new PublicBuildingDto(UUID.randomUUID(), "City Hall", "Main St 1",
                        BigDecimal.ZERO, EnergyUnit.kW, List.of()),
                new PublicBuildingDto(UUID.randomUUID(), "Library", "Oak Ave 5",
                        BigDecimal.ZERO, EnergyUnit.kW, List.of())
        );

        List<PublicBuildingResponse> responses = BuildingResponseMapper.toResponseList(dtos);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).name()).isEqualTo("City Hall");
        assertThat(responses.get(1).name()).isEqualTo("Library");
    }
}
