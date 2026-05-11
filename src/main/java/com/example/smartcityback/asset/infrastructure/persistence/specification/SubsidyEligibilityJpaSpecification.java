package com.example.smartcityback.asset.infrastructure.persistence.specification;

import com.example.smartcityback.asset.infrastructure.persistence.entity.EnergyDeviceJpaEntity;
import com.example.smartcityback.asset.infrastructure.persistence.entity.PublicBuildingJpaEntity;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

/**
 * JPA Specification — translates the subsidy eligibility business rule into a SQL WHERE clause.
 *
 * Mirrors the rule defined in SubsidyEligibilitySpecification (domain layer), but operates
 * at the database level so that only eligible buildings are fetched — no full table scan needed.
 *
 * DDD Specification  → checks an already-loaded in-memory aggregate (used inside domain/application layer)
 * JPA Specification  → generates SQL to filter at the DB level (used inside infrastructure layer)
 *
 * Both express the same rule. The JPA variant lives here (infrastructure) because it knows
 * about JPA entities, column names, and the Criteria API — all infrastructure concerns.
 *
 * Usage example (repository):
 *   List<PublicBuildingJpaEntity> eligible =
 *       jpaRepository.findAll(new SubsidyEligibilityJpaSpecification());
 */
public class SubsidyEligibilityJpaSpecification implements Specification<PublicBuildingJpaEntity> {

    private static final BigDecimal MIN_CONSUMPTION_VALUE = new BigDecimal("50");
    private static final long       MIN_DEVICES           = 2L;
    private static final String     ELIGIBLE_ZONE_PREFIX  = "Zone A%";

    @Override
    public Predicate toPredicate(Root<PublicBuildingJpaEntity> root,
                                 CriteriaQuery<?> query,
                                 CriteriaBuilder cb) {

        // consumption_value > 50
        Predicate consumptionOk = cb.greaterThan(
                root.get("consumption").get("value"),
                MIN_CONSUMPTION_VALUE
        );

        // location LIKE 'Zone A%'
        Predicate zoneOk = cb.like(root.get("location"), ELIGIBLE_ZONE_PREFIX);

        // (SELECT COUNT(*) FROM energy_devices WHERE building_id = building.id) >= 2
        // Subquery is required because device count lives in a separate table.
        Subquery<Long> deviceCountSubquery = query.subquery(Long.class);
        Root<EnergyDeviceJpaEntity> device = deviceCountSubquery.from(EnergyDeviceJpaEntity.class);
        deviceCountSubquery
                .select(cb.count(device))
                .where(cb.equal(device.get("building"), root));

        Predicate devicesOk = cb.greaterThanOrEqualTo(deviceCountSubquery, MIN_DEVICES);

        return cb.and(consumptionOk, zoneOk, devicesOk);
    }
}
