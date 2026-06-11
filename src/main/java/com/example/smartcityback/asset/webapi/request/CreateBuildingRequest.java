package com.example.smartcityback.asset.webapi.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema
public record CreateBuildingRequest(
        @NotBlank String name,
        @NotBlank String location
) {}
