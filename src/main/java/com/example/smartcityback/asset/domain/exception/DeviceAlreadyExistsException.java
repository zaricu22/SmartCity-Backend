package com.example.smartcityback.asset.domain.exception;

import com.example.smartcityback.asset.domain.shared.enums.ErrorCode;

public class DeviceAlreadyExistsException extends BusinessRuleViolationException {

    public DeviceAlreadyExistsException() {
        super(
                "Energetska jedinica vec postoji u okviru javnog objekta!",
                ErrorCode.DEVICE_ALREADY_EXISTS
        );
    }
}
