package com.example.smartcityback.asset.infrastructure.persistence.mapper;

import com.example.smartcityback.asset.domain.entity.EnergyDevice;
import com.example.smartcityback.asset.domain.valueobject.Energy;
import com.example.smartcityback.asset.infrastructure.persistence.embedded.EnergyEmbeddable;
import com.example.smartcityback.asset.infrastructure.persistence.entity.EnergyDeviceJpaEntity;
import com.example.smartcityback.asset.infrastructure.persistence.entity.PublicBuildingJpaEntity;

public class EnergyDeviceMapper {

    private EnergyDeviceMapper() {}

    public static EnergyDevice toDomain(EnergyDeviceJpaEntity entity) {
        return EnergyDevice.reconstitute(
                entity.getId(),
                entity.getName(),
                entity.getType(),
                Energy.reconstitute(entity.getRatedCapacity().getValue(),
                                    entity.getRatedCapacity().getUnit()),
                Energy.reconstitute(entity.getProduction().getValue(),
                                    entity.getProduction().getUnit())
        );
    }

    public static EnergyDeviceJpaEntity toJpa(EnergyDevice device, PublicBuildingJpaEntity building) {

        EnergyDeviceJpaEntity entity = new EnergyDeviceJpaEntity();

        entity.setId(device.getId());
        entity.setName(device.getName());
        entity.setType(device.getType());

        entity.setRatedCapacity(
                new EnergyEmbeddable(
                        device.getDeviceRatedCapacity().value(),
                        device.getDeviceRatedCapacity().unit()
                )
        );

        entity.setProduction(
                new EnergyEmbeddable(
                        device.getProductionRate().value(),
                        device.getProductionRate().unit()
                )
        );

        entity.setBuilding(building);

        return entity;
    }
}
