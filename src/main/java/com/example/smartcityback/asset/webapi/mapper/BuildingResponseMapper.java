package com.example.smartcityback.asset.webapi.mapper;

import com.example.smartcityback.asset.application.dto.PublicBuildingDto;
import com.example.smartcityback.asset.webapi.response.EnergyDeviceResponse;
import com.example.smartcityback.asset.webapi.response.PublicBuildingResponse;

import java.util.List;

public class BuildingResponseMapper {

    private BuildingResponseMapper() {}

    public static PublicBuildingResponse toResponse(PublicBuildingDto dto) {
        return new PublicBuildingResponse(
                dto.id(),
                dto.name(),
                dto.location(),
                dto.consumptionValue(),
                dto.consumptionUnit(),
                dto.devices()
                        .stream()
                        .map(d -> new EnergyDeviceResponse(
                                d.id(),
                                d.type(),
                                d.ratedCapacityValue(),
                                d.ratedCapacityUnit(),
                                d.productionRateValue(),
                                d.productionRateUnit()
                        ))
                        .toList()
        );
    }

    public static List<PublicBuildingResponse> toResponseList(List<PublicBuildingDto> dtos) {
        return dtos.stream()
                .map(BuildingResponseMapper::toResponse)
                .toList();
    }
}



