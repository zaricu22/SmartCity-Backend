package com.example.smartcityback.asset.domain.exception;

import com.example.smartcityback.asset.domain.shared.enums.ErrorCode;

public class DeviceInUseException extends BusinessRuleViolationException{
    public DeviceInUseException() {
        super(
                "Device cannot be removed because current consumption depends on it!",
                ErrorCode.DEVICE_IN_USE
        );
    }
}
