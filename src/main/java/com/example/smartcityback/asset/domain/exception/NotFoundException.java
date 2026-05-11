package com.example.smartcityback.asset.domain.exception;

import com.example.smartcityback.asset.domain.shared.enums.ErrorCode;

public abstract class NotFoundException extends DomainException {

    protected NotFoundException(String message, ErrorCode errorCode) {
        super(message, errorCode);
    }
}
