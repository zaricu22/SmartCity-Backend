package com.example.smartcityback.asset.infrastructure.persistence.interfaces;

import com.example.smartcityback.asset.infrastructure.persistence.entity.PublicBuildingJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface PublicBuildingJpaRepository
        extends JpaRepository<PublicBuildingJpaEntity, UUID>,
                JpaSpecificationExecutor<PublicBuildingJpaEntity> {
}
