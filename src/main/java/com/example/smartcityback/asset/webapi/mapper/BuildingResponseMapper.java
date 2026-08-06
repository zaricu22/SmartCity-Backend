package com.example.smartcityback.asset.webapi.mapper;

import com.example.smartcityback.asset.application.dto.PublicBuildingDto;
import com.example.smartcityback.asset.shared.PagedResult;
import com.example.smartcityback.asset.webapi.response.EnergyDeviceResponse;
import com.example.smartcityback.asset.webapi.response.PagedResponse;
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
                                d.name(),
                                d.type(),
                                d.ratedCapacityValue(),
                                d.ratedCapacityUnit(),
                                d.productionRateValue(),
                                d.productionRateUnit()
                        ))
                        .toList(),
                dto.version()
        );
    }

    public static List<PublicBuildingResponse> toResponseList(List<PublicBuildingDto> dtos) {
        return dtos.stream()
                .map(BuildingResponseMapper::toResponse)
                .toList();
    }

    public static PagedResponse<PublicBuildingResponse> toResponsePage(PagedResult<PublicBuildingDto> paged) {
        return new PagedResponse<>(
                toResponseList(paged.content()),
                paged.totalElements(),
                paged.totalPages(),
                paged.pageNumber(),
                paged.pageSize()
        );
    }
}



