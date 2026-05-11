package com.example.smartcityback.asset.infrastructure.persistence.mapper;

import com.example.smartcityback.asset.domain.aggregate.PublicBuilding;
import com.example.smartcityback.asset.domain.valueobject.Energy;
import com.example.smartcityback.asset.infrastructure.persistence.embedded.EnergyEmbeddable;
import com.example.smartcityback.asset.infrastructure.persistence.entity.EnergyDeviceJpaEntity;
import com.example.smartcityback.asset.infrastructure.persistence.entity.PublicBuildingJpaEntity;

import java.util.List;

public class PublicBuildingMapper {

    private PublicBuildingMapper() {}

    public static PublicBuilding toDomain(PublicBuildingJpaEntity entity) {

        PublicBuilding building =
                new PublicBuilding(
                        entity.getId(),
                        entity.getName(),
                        entity.getLocation()
                );

        entity.getDevices()
                .forEach(device -> building.addDevice(
                        EnergyDeviceMapper.toDomain(device)
                ));

        building.changeConsumption(
                new Energy(
                        entity.getConsumption().getValue(),
                        entity.getConsumption().getUnit()
                )
        );

        return building;
    }

    public static PublicBuildingJpaEntity toJpa(PublicBuilding building) {

        PublicBuildingJpaEntity entity =
                new PublicBuildingJpaEntity(
                        building.getId(),
                        building.getName(),
                        building.getLocation()
                );

        List<EnergyDeviceJpaEntity> devices =
                building.getDevices()
                        .stream()
                        .map(d -> EnergyDeviceMapper.toJpa(d, entity))
                        .toList();
        entity.setDevices(devices);

        entity.setConsumption(
                new EnergyEmbeddable(
                        building.getConsumption().value(),
                        building.getConsumption().unit()
                )
        );

        return entity;
    }

    public static void updateJpaEntity(PublicBuildingJpaEntity existing, PublicBuilding building) {
        existing.setName(building.getName());
        existing.setLocation(building.getLocation());

        // Because of orphanRemoval = true, otherwise Hibernate do not allow to just set new list
        existing.getDevices().clear();
        existing.getDevices().addAll(building.getDevices()
                .stream()
                .map(d -> EnergyDeviceMapper.toJpa(d, existing))
                .toList());

        existing.setConsumption(
                new EnergyEmbeddable(
                        building.getConsumption().value(),
                        building.getConsumption().unit()
                )
        );
    }
}
