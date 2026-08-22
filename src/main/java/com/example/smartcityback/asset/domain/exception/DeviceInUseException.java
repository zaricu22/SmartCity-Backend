package com.example.smartcityback.asset.domain.exception;

import com.example.smartcityback.asset.domain.shared.enums.ErrorCode;

public class DeviceInUseException extends BusinessRuleViolationException{
    public DeviceInUseException() {
        super(
                "Active device cannot be removed!",
                ErrorCode.DEVICE_IN_USE
        );
    }
}
