package com.example.smartcityback.asset.domain.exception;

import com.example.smartcityback.asset.domain.shared.enums.ErrorCode;

public class BuildingProductionRateExceededException extends BusinessRuleViolationException {

    public BuildingProductionRateExceededException() {
        super(
                "Building consumption exceeds its total energy production rate!",
                ErrorCode.PRODUCTION_RATE_EXCEEDED
        );
    }
}
