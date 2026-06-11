package com.example.smartcityback.asset.webapi.exception;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema
public record ErrorResponse(
        String errorCode,
        String message,
        int status,
        Instant timestamp,
        String requestId
) {}
