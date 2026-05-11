package com.example.smartcityback.asset.domain.exception;

import com.example.smartcityback.asset.domain.shared.enums.ErrorCode;

public class BuildingTotalCapacityExceededException extends BusinessRuleViolationException {

    public BuildingTotalCapacityExceededException() {
        super(
                "Potrosnja objekta premasuje njegov ukupni energetski kapacitet!",
                ErrorCode.TOTAL_CAPACITY_EXCEEDED
        );
    }
}
