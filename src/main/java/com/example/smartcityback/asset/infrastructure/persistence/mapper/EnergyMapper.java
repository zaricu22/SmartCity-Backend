package com.example.smartcityback.asset.infrastructure.persistence.mapper;

import com.example.smartcityback.asset.domain.valueobject.Energy;
import com.example.smartcityback.asset.infrastructure.persistence.embedded.EnergyEmbeddable;

public class EnergyMapper {

    private EnergyMapper() {}

    public static Energy toDomain(EnergyEmbeddable emb) {
        return new Energy(emb.getValue(), emb.getUnit()
        );
    }

    public static EnergyEmbeddable toJpa(Energy domain) {
        return new EnergyEmbeddable(domain.value(), domain.unit()
        );
    }
}
