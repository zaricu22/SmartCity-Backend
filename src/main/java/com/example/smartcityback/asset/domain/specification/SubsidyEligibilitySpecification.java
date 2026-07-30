package com.example.smartcityback.asset.domain.specification;

import com.example.smartcityback.asset.domain.aggregate.PublicBuilding;
import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;
import com.example.smartcityback.asset.domain.valueobject.Energy;

import java.math.BigDecimal;

/**
 * DDD Specification — encapsulates the business rule for government energy subsidy eligibility.
 *
 * A building is eligible if:
 *   - it has at least 2 energy devices
 *   - its consumption exceeds 50 kW
 *
 * Purpose: centralizes this rule so it can be reused across multiple services without duplication and complex if-logic.
 * The rule is defined once, tested once, and injected wherever needed.
 *
 * If the eligibility rule changes (e.g. minimum devices raised to 3), only this class needs to be updated.
 */
public class SubsidyEligibilitySpecification {

    private static final Energy MIN_CONSUMPTION = new Energy(new BigDecimal("50"), EnergyUnit.kW);
    private static final int    MIN_DEVICES      = 2;

    public boolean isSatisfiedBy(PublicBuilding building) {
        return building.getDevices().size() >= MIN_DEVICES
                && building.getConsumption().greaterThan(MIN_CONSUMPTION);
    }
}
