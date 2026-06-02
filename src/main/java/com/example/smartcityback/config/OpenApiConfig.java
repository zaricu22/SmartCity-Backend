package com.example.smartcityback.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "SmartCity Backend API",
                version = "1.0",
                description = "REST API for managing public buildings and energy devices in a smart city"
        )
)
public class OpenApiConfig {
}
