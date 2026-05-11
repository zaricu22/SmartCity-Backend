package com.example.smartcityback.asset.webapi.external;

public class ExternalServiceTimeoutException extends ExternalServiceException {

    public ExternalServiceTimeoutException(Throwable cause) {
        super("External service timed out", cause);
    }
}
