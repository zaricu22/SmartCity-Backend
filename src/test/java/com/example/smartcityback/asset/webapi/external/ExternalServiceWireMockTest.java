package com.example.smartcityback.asset.webapi.external;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Integration test template for external API clients using WireMock.
 *
 * It starts a real HTTP server locally that pretends to be the external service.
 * Instead of calling the real API, your application calls WireMock,
 * and WireMock returns whatever response you configured with stubFor().
 *
 * SCENARIOS TO COVER PER EACH EXTERNAL CLIENT CALL:
 *   - Happy path (200 OK with expected body)
 *   - Server error (500) — assert fallback / exception
 *   - Timeout — assert timeout handling
 *   - Unexpected response shape — assert error handling
 */


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@ActiveProfiles("test")
class ExternalServiceWireMockTest {

    // -------------------------------------------------------------------------
    // Testcontainer: temporary real instance of PostgreSQL for any repository interactions during tests.
    // -------------------------------------------------------------------------

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("smartcity_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // -------------------------------------------------------------------------
    // WireMock server — static so its port is available in @DynamicPropertySource
    // -------------------------------------------------------------------------

    static WireMockServer wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        wireMock.start();
    }

    @DynamicPropertySource
    static void configureExternalServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("energy-price.base-url", () -> "http://localhost:" + wireMock.port());
    }

    @BeforeEach
    void resetWireMock() {
        WireMock.configureFor("localhost", wireMock.port());
        wireMock.resetAll();
    }

    @Autowired
    private EnergyPriceClient energyPriceClient;

    // -------------------------------------------------------------------------
    // Example: replace this with real stubs when an external client is added
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("fetches the current energy price (0.12 EUR/kWh) from a stubbed server standing in for the "
            + "real pricing API")
    void wireMock_serverStartsAndAcceptsStubs() {
        // Stub a happy path response — verify WireMock is working correctly:
           wireMock.stubFor(get(urlEqualTo("/api/v1/energy-prices"))
               .willReturn(aResponse()
                   .withStatus(200)
                   .withHeader("Content-Type", "application/json")
                   .withBody("""
                           { "pricePerKWh": 0.12, "currency": "EUR" }
                           """)));

        // Call stubbed endpoint via client:
           BigDecimal price = energyPriceClient.getCurrentPrice();
           assertThat(price).isEqualByComparingTo("0.12");
        assertThat(true).isTrue();
    }

    @Test
    @DisplayName("turns a 500 error from the upstream pricing service into an ExternalServiceException, instead "
            + "of letting the raw HTTP failure escape to the caller")
    void wireMock_externalServiceReturns500_applicationHandlesGracefully() {
        // Stub a server error — verify no unhandled exception escapes the client:
           wireMock.stubFor(get(urlEqualTo("/api/v1/energy-prices"))
                   .willReturn(aResponse().withStatus(500)));

        // Call stubbed endpoint via client:
           assertThatThrownBy(() -> energyPriceClient.getCurrentPrice())
               .isInstanceOf(ExternalServiceException.class);
        assertThat(true).isTrue();
    }

    @Test
    @DisplayName("turns an upstream response that takes 5 seconds — longer than the client's configured "
            + "timeout — into an ExternalServiceTimeoutException")
    void wireMock_externalServiceTimesOut_applicationHandlesGracefully() {
        // Stub a delayed response exceeding the client's timeout threshold:
           wireMock.stubFor(get(urlEqualTo("/api/v1/energy-prices"))
                   .willReturn(aResponse()
                           .withStatus(200)
                           .withFixedDelay(5000))); // 5 seconds — should exceed client timeout

        // Call stubbed endpoint via client:
           assertThatThrownBy(() -> energyPriceClient.getCurrentPrice())
               .isInstanceOf(ExternalServiceTimeoutException.class);
        assertThat(true).isTrue();
    }

    @Test
    @DisplayName("turns an upstream response with an unrecognized JSON shape (missing the expected price field) "
            + "into an ExternalServiceException, instead of a raw deserialization crash")
    void wireMock_externalServiceReturnsUnexpectedShape_applicationHandlesGracefully() {
        // Stub a response with missing/malformed fields — verify deserialization failure is handled:
           wireMock.stubFor(get(urlEqualTo("/api/v1/energy-prices"))
                   .willReturn(aResponse()
                           .withStatus(200)
                           .withHeader("Content-Type", "application/json")
                           .withBody("""
                                   { "unexpectedField": "unexpectedValue" }
                                   """)));

        // Call stubbed endpoint via client:
           assertThatThrownBy(() -> energyPriceClient.getCurrentPrice())
               .isInstanceOf(ExternalServiceException.class);
        assertThat(true).isTrue();
    }
}
