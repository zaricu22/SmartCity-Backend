package com.example.smartcityback.asset.domain.exception;

import com.example.smartcityback.asset.domain.shared.enums.ErrorCode;

public class DeviceCapacityLimitException extends BusinessRuleViolationException {

    public DeviceCapacityLimitException() {
        super(
                "Zatrazena proizvodnja energije premasuje oprativni opseg jedinice!",
                ErrorCode.DEVICE_CAPACITY_OUT_OF_RANGE
        );
    }
}
