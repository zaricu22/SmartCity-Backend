package com.example.smartcityback.asset.domain.repository;

import com.example.smartcityback.asset.domain.aggregate.PublicBuilding;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PublicBuildingRepository {
    Optional<PublicBuilding> findById(UUID id);
    List<PublicBuilding> findAll();
    void save(PublicBuilding building);
    void delete(UUID building);
}
