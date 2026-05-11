package com.example.smartcityback.asset.domain.exception;

import com.example.smartcityback.asset.domain.shared.enums.ErrorCode;

public class ValidationException extends DomainException {

    public ValidationException(String message, ErrorCode errorCode) {
        super(message, errorCode);
    }
}
