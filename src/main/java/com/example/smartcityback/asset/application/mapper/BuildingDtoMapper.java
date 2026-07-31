package com.example.smartcityback.asset.application.mapper;

import com.example.smartcityback.asset.application.dto.EnergyDeviceDto;
import com.example.smartcityback.asset.application.dto.PublicBuildingDto;
import com.example.smartcityback.asset.domain.aggregate.PublicBuilding;

public class BuildingDtoMapper {

    private BuildingDtoMapper() {}

    public static PublicBuildingDto toDto(PublicBuilding building) {
        return new PublicBuildingDto(
                building.getId(),
                building.getName(),
                building.getLocation(),
                building.getConsumption().value(),
                building.getConsumption().unit(),
                building.getDevices()
                        .stream()
                        .map(d -> new EnergyDeviceDto(
                                d.getId(),
                                d.getName(),
                                d.getType(),
                                d.getDeviceRatedCapacity().value(),
                                d.getDeviceRatedCapacity().unit(),
                                d.getProductionRate().value(),
                                d.getProductionRate().unit()
                        ))
                        .toList()
        );
    }
}
