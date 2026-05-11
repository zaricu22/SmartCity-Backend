package com.example.smartcityback.asset.application.exception;

import com.example.smartcityback.asset.domain.shared.enums.ErrorCode;

public abstract class ApplicationException extends RuntimeException {

    private final ErrorCode errorCode;

    protected ApplicationException(String message, ErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
