package com.example.smartcityback.asset.domain.exception;

import com.example.smartcityback.asset.domain.shared.enums.ErrorCode;

public class BuildingTotalCapacityExceededException extends BusinessRuleViolationException {

    public BuildingTotalCapacityExceededException() {
        super(
                "Building consumption exceeds its total energy capacity!",
                ErrorCode.TOTAL_CAPACITY_EXCEEDED
        );
    }
}
