package com.example.smartcityback.asset.webapi.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema
public record RemoveDeviceRequest(
        @NotNull Long version
) {}
