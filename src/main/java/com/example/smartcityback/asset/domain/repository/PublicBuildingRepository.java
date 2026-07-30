package com.example.smartcityback.asset.domain.repository;

import com.example.smartcityback.asset.domain.aggregate.PublicBuilding;
import com.example.smartcityback.asset.domain.readmodel.PublicBuildingSummary;
import com.example.smartcityback.asset.shared.PagedResult;

import java.util.Optional;
import java.util.UUID;

public interface PublicBuildingRepository {
    Optional<PublicBuilding> findById(UUID id);
    PagedResult<PublicBuilding> findAll(int page, int size, String sortBy, String sortDir);
    PagedResult<PublicBuildingSummary> findEligibleForSubsidy(int page, int size, String sortBy, String sortDir);
    void save(PublicBuilding building);
    void delete(UUID building);
}
