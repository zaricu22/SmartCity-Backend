package com.example.smartcityback.asset.webapi.external;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;


@Component
public class EnergyPriceClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public EnergyPriceClient(RestTemplate restTemplate,
                              @Value("${energy-price.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public BigDecimal getCurrentPrice() {
        EnergyPriceResponse response = restTemplate.getForObject(
                baseUrl + "/api/v1/energy-prices",
                EnergyPriceResponse.class
        );
        return response.pricePerKWh();
    }
}
