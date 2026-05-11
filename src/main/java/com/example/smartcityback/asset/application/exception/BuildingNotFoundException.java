package com.example.smartcityback.asset.application.exception;

import com.example.smartcityback.asset.domain.exception.NotFoundException;
import com.example.smartcityback.asset.domain.shared.enums.ErrorCode;

public class BuildingNotFoundException extends NotFoundException {

    public BuildingNotFoundException() {
        super(
                "Javni objekat nije pronadjen!",
                ErrorCode.BUILDING_NOT_FOUND
        );
    }
}
