package com.example.smartcityback.asset.webapi.integration;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
    * Test verifies full application context — every layer participates in the real flow:
        * HTTP request → Controller → Service → Repository → PostgreSQL(Testcontainers)
                                                                ↓
        * HTTP response ← GlobalExceptionHandler ← (exception propagates up)
    * Service is NOT mocked, so we cannot reliably control and force exception to be thrown
    * The IntegrationE2ETest should only keep what it uniquely tests — full real stack (real service + real DB)
 */

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class PublicBuildingFullIntegrationTest {

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

    @LocalServerPort
    private int port;

    @Value("${app.security.admin.password}")
    private String adminPassword;

    private RequestSpecification asAdmin;

    @BeforeEach
    void setUp() {
        RestAssured.port        = port;
        RestAssured.basePath    = "/SmartCityREST";
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        String token = given()
                .contentType(ContentType.JSON)
                .body("""
                    { "username": "admin", "password": "%s" }
                    """.formatted(adminPassword))
                .when()
                .post("/v1/auth/login")
                .then()
                .statusCode(200)
                .extract().jsonPath().getString("token");

        asAdmin = new RequestSpecBuilder()
                .addHeader("Authorization", "Bearer " + token)
                .build();
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private UUID createBuilding() {
        return given(asAdmin)
                .contentType(ContentType.JSON)
                .body("""
                    { "name": "City Hall", "location": "Main St 1" }
                    """)
                .when()
                .post("/v1/buildings")
                .then()
                .statusCode(201)
                .extract().as(UUID.class);
    }

    private UUID createBuildingWithDevice() {
        UUID buildingId = createBuilding();

        given(asAdmin)
                .contentType(ContentType.JSON)
                .body("""
                    { "name": "Solar Panel 1", "type": "SOLAR", "ratedCapacityValue": 100, "ratedCapacityUnit": "kW" }
                    """)
                .when()
                .post("/v1/buildings/{id}/devices", buildingId)
                .then()
                .statusCode(204);

        return buildingId;
    }

    // =====================================================================
    // Happy path: valid requests succeed with correct status codes
    // =====================================================================

    @Test
    void createBuilding_validRequest_returns201() {
        given(asAdmin)
                .contentType(ContentType.JSON)
                .body("""
                    { "name": "City Hall", "location": "Main St 1" }
                    """)
                .when()
                .post("/v1/buildings")
                .then()
                .statusCode(201)
                .body(notNullValue());
    }

    @Test
    void addDevice_validRequest_returns204() {
        UUID buildingId = createBuilding();

        given(asAdmin)
                .contentType(ContentType.JSON)
                .body("""
                    { "name": "Solar Panel 1", "type": "SOLAR", "ratedCapacityValue": 100, "ratedCapacityUnit": "kW" }
                    """)
                .when()
                .post("/v1/buildings/{id}/devices", buildingId)
                .then()
                .statusCode(204);
    }

    @Test
    void changeConsumption_validRequest_returns204() {
        UUID buildingId = createBuildingWithDevice();

        given(asAdmin)
                .contentType(ContentType.JSON)
                .body("""
                    { "consumptionValue": 50, "consumptionUnit": "kW" }
                    """)
                .when()
                .patch("/v1/buildings/{id}/consumption", buildingId)
                .then()
                .statusCode(204);
    }

    @Test
    void changeProduction_validRequest_returns204() {
        UUID buildingId = createBuildingWithDevice();
        UUID deviceId = given(asAdmin)
                .when()
                .get("/v1/buildings/{id}", buildingId)
                .then()
                .statusCode(200)
                .extract().jsonPath().getUUID("devices[0].id");

        given(asAdmin)
                .contentType(ContentType.JSON)
                .body("""
                    { "productionValue": 60, "productionUnit": "kW" }
                    """)
                .when()
                .patch("/v1/buildings/{buildingId}/devices/{deviceId}/production", buildingId, deviceId)
                .then()
                .statusCode(204);
    }

}
