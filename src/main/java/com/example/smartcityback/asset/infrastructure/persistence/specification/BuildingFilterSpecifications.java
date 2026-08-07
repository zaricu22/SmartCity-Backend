package com.example.smartcityback.asset.infrastructure.persistence.specification;

import com.example.smartcityback.asset.infrastructure.persistence.entity.PublicBuildingJpaEntity;
import org.springframework.data.jpa.domain.Specification;

/**
 * Composable filter predicates for GET /v1/buildings — each factory method returns null when its
 * filter value is absent, and Specification.where(...).and(...) treats a null Specification as
 * "no restriction", so callers can freely combine any subset of these without null-checking.
 */
public class BuildingFilterSpecifications {

    private BuildingFilterSpecifications() {}

    public static Specification<PublicBuildingJpaEntity> nameContains(String name) {
        if (name == null || name.isBlank()) return null;
        String pattern = "%" + name.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern);
    }

    public static Specification<PublicBuildingJpaEntity> locationContains(String location) {
        if (location == null || location.isBlank()) return null;
        String pattern = "%" + location.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("location")), pattern);
    }
}
