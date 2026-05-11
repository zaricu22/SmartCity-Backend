package com.example.smartcityback.asset.webapi.request;

import jakarta.validation.constraints.NotBlank;

public record CreateBuildingRequest(
        @NotBlank String name,
        @NotBlank String location
) {}
