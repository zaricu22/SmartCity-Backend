package com.example.smartcityback.asset.domain.exception;


import com.example.smartcityback.asset.domain.shared.enums.ErrorCode;

public class DeviceNotFoundException extends NotFoundException {

    public DeviceNotFoundException() {
        super(
                "Device not found!",
                ErrorCode.DEVICE_NOT_FOUND
        );
    }
}
