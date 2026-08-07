package com.example.smartcityback.asset.domain.exception;

import com.example.smartcityback.asset.domain.shared.enums.ErrorCode;

public class BuildingAlreadyExistsException extends BusinessRuleViolationException {

    public BuildingAlreadyExistsException() {
        super(
                "A building with this name and location already exists!",
                ErrorCode.BUILDING_ALREADY_EXISTS
        );
    }
}
