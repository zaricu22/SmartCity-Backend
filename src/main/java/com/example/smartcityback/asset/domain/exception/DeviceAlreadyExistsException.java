package com.example.smartcityback.asset.domain.exception;

import com.example.smartcityback.asset.domain.shared.enums.ErrorCode;

public class DeviceAlreadyExistsException extends BusinessRuleViolationException {

    public DeviceAlreadyExistsException() {
        super(
                "Device already exists in this building!",
                ErrorCode.DEVICE_ALREADY_EXISTS
        );
    }
}
