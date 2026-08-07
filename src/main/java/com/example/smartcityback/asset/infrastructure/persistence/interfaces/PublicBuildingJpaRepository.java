package com.example.smartcityback.asset.infrastructure.persistence.interfaces;

import com.example.smartcityback.asset.infrastructure.persistence.entity.PublicBuildingJpaEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface PublicBuildingJpaRepository
        extends JpaRepository<PublicBuildingJpaEntity, UUID>,
                JpaSpecificationExecutor<PublicBuildingJpaEntity> {
    boolean existsByNameAndLocation(String name, String location);

    // Joins devices into the same query instead of a separate lazy-load SELECT after — collapses
    // findById() from 2 queries to 1. Suitable here specifically because this fetches a single
    // building by ID with no LIMIT/OFFSET involved, so there's no result set for the JOIN to
    // multiply and nothing for Hibernate to paginate incorrectly — the trap that rules out the
    // same approach on the paginated list query (see @BatchSize on PublicBuildingJpaEntity.devices).
    @EntityGraph(attributePaths = "devices")
    @Override
    Optional<PublicBuildingJpaEntity> findById(UUID id);
}
