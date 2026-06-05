package com.example.smartcityback.asset.domain.exception;

import com.example.smartcityback.asset.domain.shared.enums.ErrorCode;

public abstract class BusinessRuleViolationException extends DomainException {

    protected BusinessRuleViolationException(String message, ErrorCode errorCode) {
        super(message, errorCode);
    }
}
