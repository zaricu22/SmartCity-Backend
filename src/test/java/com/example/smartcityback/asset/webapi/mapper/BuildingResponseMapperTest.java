package com.example.smartcityback.asset.webapi.mapper;

import com.example.smartcityback.asset.application.dto.EnergyDeviceDto;
import com.example.smartcityback.asset.application.dto.PublicBuildingDto;
import com.example.smartcityback.asset.domain.shared.enums.DeviceType;
import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;
import com.example.smartcityback.asset.webapi.response.PublicBuildingResponse;
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("maps a building DTO's ID, name, location, consumption, and version into the HTTP response")
    void toResponse_mapsAllScalarFields() {
        PublicBuildingDto dto = new PublicBuildingDto(
                BUILDING_ID, "City Hall", "Main St 1",
                new BigDecimal("50"), EnergyUnit.kW,
                List.of(), 3L
        );

        PublicBuildingResponse response = BuildingResponseMapper.toResponse(dto);

        assertThat(response.id()).isEqualTo(BUILDING_ID);
        assertThat(response.name()).isEqualTo("City Hall");
        assertThat(response.location()).isEqualTo("Main St 1");
        assertThat(response.consumptionValue()).isEqualByComparingTo("50");
        assertThat(response.consumptionUnit()).isEqualTo(EnergyUnit.kW);
        assertThat(response.version()).isEqualTo(3L);
    }

    @Test
    @DisplayName("maps a building with no devices to a response with an empty device list, not a null one")
    void toResponse_noDevices_returnsEmptyDeviceList() {
        PublicBuildingDto dto = new PublicBuildingDto(
                BUILDING_ID, "City Hall", "Main St 1",
                BigDecimal.ZERO, EnergyUnit.kW,
                List.of(), 0L
        );

        PublicBuildingResponse response = BuildingResponseMapper.toResponse(dto);

        assertThat(response.devices()).isEmpty();
    }

    @Test
    @DisplayName("maps a device's ID, name, type, rated capacity, and production rate into the response's "
            + "device list")
    void toResponse_withDevice_mapsDeviceFields() {
        PublicBuildingDto dto = new PublicBuildingDto(
                BUILDING_ID, "City Hall", "Main St 1",
                BigDecimal.ZERO, EnergyUnit.kW,
                List.of(new EnergyDeviceDto(DEVICE_ID, "Test Device", DeviceType.SOLAR, new BigDecimal("100"), EnergyUnit.kW, new BigDecimal("60"), EnergyUnit.kW)),
                0L
        );

        PublicBuildingResponse response = BuildingResponseMapper.toResponse(dto);

        assertThat(response.devices()).hasSize(1);
        assertThat(response.devices().get(0).id()).isEqualTo(DEVICE_ID);
        assertThat(response.devices().get(0).name()).isEqualTo("Test Device");
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
    @DisplayName("maps an empty list of building DTOs to an empty list of responses")
    void toResponseList_emptyList_returnsEmptyList() {
        List<PublicBuildingResponse> responses = BuildingResponseMapper.toResponseList(List.of());

        assertThat(responses).isEmpty();
    }

    @Test
    @DisplayName("maps a list of building DTOs to responses in the same order, not just mapping the first one")
    void toResponseList_multipleBuildings_mapsAll() {
        List<PublicBuildingDto> dtos = List.of(
                new PublicBuildingDto(UUID.randomUUID(), "City Hall", "Main St 1",
                        BigDecimal.ZERO, EnergyUnit.kW, List.of(), 0L),
                new PublicBuildingDto(UUID.randomUUID(), "Library", "Oak Ave 5",
                        BigDecimal.ZERO, EnergyUnit.kW, List.of(), 0L)
        );

        List<PublicBuildingResponse> responses = BuildingResponseMapper.toResponseList(dtos);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).name()).isEqualTo("City Hall");
        assertThat(responses.get(1).name()).isEqualTo("Library");
    }
}
