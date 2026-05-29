package com.example.smartcityback.asset.webapi.external;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;

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
        try {
            EnergyPriceResponse response = restTemplate.getForObject(
                    baseUrl + "/api/v1/energy-prices",
                    EnergyPriceResponse.class
            );
            if (response == null || response.pricePerKWh() == null) {
                throw new ExternalServiceException("Invalid response from energy price service", null);
            }
            return response.pricePerKWh();
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof SocketTimeoutException) {
                throw new ExternalServiceTimeoutException(e);
            }
            throw new ExternalServiceException("Failed to reach energy price service", e);
        } catch (RestClientException e) {
            throw new ExternalServiceException("Energy price service error", e);
        }
    }
}
