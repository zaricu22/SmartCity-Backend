package com.example.smartcityback.asset.webapi.controller;

import com.example.smartcityback.asset.application.dto.EnergyDeviceDto;
import com.example.smartcityback.asset.application.dto.PublicBuildingDto;
import com.example.smartcityback.asset.application.exception.BuildingNotFoundException;
import com.example.smartcityback.asset.application.service.PublicBuildingAppService;
import com.example.smartcityback.asset.domain.exception.BuildingTotalCapacityExceededException;
import com.example.smartcityback.asset.domain.exception.DeviceCapacityLimitException;
import com.example.smartcityback.asset.domain.exception.DeviceAlreadyExistsException;
import com.example.smartcityback.asset.domain.exception.DeviceNotFoundException;
import com.example.smartcityback.asset.domain.exception.ValidationException;
import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;
import com.example.smartcityback.asset.domain.shared.enums.ErrorCode;
import com.example.smartcityback.asset.application.service.PublicBuildingQueryService;
import com.example.smartcityback.auth.infrastructure.JwtTokenService;
import com.example.smartcityback.auth.infrastructure.TokenBlacklist;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The @WebMvcTest does not start a real servlet container so server.servlet.context-path is NOT applied.
 * Only the web layer is loaded (controller + GlobalExceptionHandler), with the service mocked via Mockito.
 * No real DB, no full context.
 *
 * Its specific purpose is to test exception → HTTP response mapping through GlobalExceptionHandler.
 * Because the service is mocked, it can force any exception to be thrown.
 *
 * Syntax:
 *    methods which return values (like create) use willReturn() for the happy path, but willThrow() for exceptions
 *    methods which return void (like addDevice) use willDoNothing() for the happy path, but willThrow() for exceptions
 */


@WebMvcTest(
        value = PublicBuildingController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class, OAuth2ClientAutoConfiguration.class}
)
@ActiveProfiles("test")
class PublicBuildingControllerAPITest {

    @Autowired
    private MockMvc mockMvc;

    // Not used, but required to load the controller and test the exception handling flow.
    @MockitoBean
    private PublicBuildingAppService appService;

    @MockitoBean
    private PublicBuildingQueryService queryService;

    // JwtAuthFilter and RateLimitFilter are @Component Filter beans — @WebMvcTest picks them up.
    // Mock their dependencies so the context loads; neither is ever invoked for /v1/buildings/* requests.
    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private TokenBlacklist tokenBlacklist;

    private static final UUID BUILDING_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID DEVICE_ID   = UUID.fromString("660e8400-e29b-41d4-a716-446655440001");

    // =====================================================================
    // Happy path: valid requests succeed with expected status codes
    // =====================================================================

    @Test
    void create_validRequest_returns201() throws Exception {
        given(appService.create(any())).willReturn(BUILDING_ID);

        mockMvc.perform(post("/v1/buildings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "City Hall", "location": "Main St 1"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(BUILDING_ID.toString())))
                .andExpect(content().string("\"" + BUILDING_ID + "\""));
    }

    @Test
    void addDevice_validRequest_returns204() throws Exception {
        mockMvc.perform(post("/v1/buildings/{id}/devices", BUILDING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "SOLAR", "ratedCapacityValue": 100, "ratedCapacityUnit": "kW"}
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    void changeConsumption_validRequest_returns204() throws Exception {
        mockMvc.perform(patch("/v1/buildings/{id}/consumption", BUILDING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consumptionValue": 50, "consumptionUnit": "kW"}
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    void getAll_returns200WithPagedResult() throws Exception {
        given(queryService.getAll(anyInt(), anyInt(), anyString(), anyString()))
                .willReturn(new com.example.smartcityback.asset.shared.PagedResult<>(List.of(), 0L, 0, 0, 20));

        mockMvc.perform(get("/v1/buildings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.pageNumber").value(0))
                .andExpect(jsonPath("$.pageSize").value(20));
    }

    @Test
    void getAll_sortDesc_passesDescDirectionToService() throws Exception {
        given(queryService.getAll(anyInt(), anyInt(), anyString(), anyString()))
                .willReturn(new com.example.smartcityback.asset.shared.PagedResult<>(List.of(), 0L, 0, 0, 20));

        mockMvc.perform(get("/v1/buildings").param("sort", "name,desc"))
                .andExpect(status().isOk());

        then(queryService).should().getAll(anyInt(), anyInt(), eq("name"), eq("desc"));
    }

    @Test
    void getAll_sortAsc_passesAscDirectionToService() throws Exception {
        given(queryService.getAll(anyInt(), anyInt(), anyString(), anyString()))
                .willReturn(new com.example.smartcityback.asset.shared.PagedResult<>(List.of(), 0L, 0, 0, 20));

        mockMvc.perform(get("/v1/buildings").param("sort", "name,asc"))
                .andExpect(status().isOk());

        then(queryService).should().getAll(anyInt(), anyInt(), eq("name"), eq("asc"));
    }

    @Test
    void getBuilding_found_returns200WithBody() throws Exception {
        PublicBuildingDto response = new PublicBuildingDto(
                BUILDING_ID, "City Hall", "Main St 1",
                BigDecimal.ZERO, EnergyUnit.kW,
                List.of(new EnergyDeviceDto(DEVICE_ID,
                        com.example.smartcityback.asset.domain.shared.enums.DeviceType.SOLAR,
                        new BigDecimal("100"), EnergyUnit.kW,
                        BigDecimal.ZERO, EnergyUnit.kW))
        );
        given(queryService.getById(BUILDING_ID)).willReturn(response);

        mockMvc.perform(get("/v1/buildings/{id}", BUILDING_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(BUILDING_ID.toString()))
                .andExpect(jsonPath("$.name").value("City Hall"))
                .andExpect(jsonPath("$.location").value("Main St 1"))
                .andExpect(jsonPath("$.devices").isArray())
                .andExpect(jsonPath("$.devices[0].type").value("SOLAR"));
    }

    @Test
    void getBuilding_notFound_returns404() throws Exception {
        given(queryService.getById(BUILDING_ID)).willThrow(new BuildingNotFoundException());

        mockMvc.perform(get("/v1/buildings/{id}", BUILDING_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUILDING_NOT_FOUND"));
    }

    @Test
    void changeProduction_validRequest_returns204() throws Exception {
        mockMvc.perform(patch("/v1/buildings/{buildingId}/devices/{deviceId}/production",
                        BUILDING_ID, DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productionValue": 60, "productionUnit": "kW"}
                                """))
                .andExpect(status().isNoContent());
    }

    // =====================================================================
    // Validation path: invalid requests fail with 422 (GlobalExceptionHandler.handleValidation())
    // =====================================================================

    @Test
    void create_domainValidationException_returns422WithDomainErrorCode() throws Exception {
        given(appService.create(any()))
                .willThrow(new ValidationException("Building must have a name!", ErrorCode.BUILDING_NAME_EMPTY));

        mockMvc.perform(post("/v1/buildings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "X", "location": "Main St 1"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("BUILDING_NAME_EMPTY"));
    }

    // =====================================================================
    // Validation path: invalid requests fail with 422 (GlobalExceptionHandler.handleSpringValidation())
    // =====================================================================

    @Test
    void create_blankName_returns422WithValidationErrorCode() throws Exception {
        mockMvc.perform(post("/v1/buildings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "location": "Main St 1"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void create_missingLocation_returns422() throws Exception {
        mockMvc.perform(post("/v1/buildings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "City Hall"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void addDevice_missingType_returns422() throws Exception {
        mockMvc.perform(post("/v1/buildings/{id}/devices", BUILDING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ratedCapacityValue": 50, "ratedCapacityUnit": "kW"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void addDevice_negativeCapacity_returns422() throws Exception {
        mockMvc.perform(post("/v1/buildings/{id}/devices", BUILDING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "SOLAR", "ratedCapacityValue": -5, "ratedCapacityUnit": "kW"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void changeConsumption_negativeValue_returns422() throws Exception {
        mockMvc.perform(patch("/v1/buildings/{id}/consumption", BUILDING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consumptionValue": -1, "consumptionUnit": "kW"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void changeProduction_negativeValue_returns422() throws Exception {
        mockMvc.perform(patch("/v1/buildings/{buildingId}/devices/{deviceId}/production",
                        BUILDING_ID, DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productionValue": -1, "productionUnit": "kW"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    // =====================================================================
    // Not Found path: invalid requests fail with 404 (GlobalExceptionHandler.handleNotFound())
    // =====================================================================

    @Test
    void addDevice_buildingNotFound_returns404() throws Exception {
        willThrow(new BuildingNotFoundException()).given(appService).addDevice(any());

        mockMvc.perform(post("/v1/buildings/{id}/devices", BUILDING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "SOLAR", "ratedCapacityValue": 100, "ratedCapacityUnit": "kW"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUILDING_NOT_FOUND"));
    }

    @Test
    void changeConsumption_buildingNotFound_returns404() throws Exception {
        willThrow(new BuildingNotFoundException()).given(appService).changeConsumption(any(), any());

        mockMvc.perform(patch("/v1/buildings/{id}/consumption", BUILDING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consumptionValue": 50, "consumptionUnit": "kW"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUILDING_NOT_FOUND"));
    }

    @Test
    void changeProduction_buildingNotFound_returns404() throws Exception {
        willThrow(new BuildingNotFoundException()).given(appService).changeProduction(any(), any(), any());

        mockMvc.perform(patch("/v1/buildings/{buildingId}/devices/{deviceId}/production",
                        BUILDING_ID, DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productionValue": 60, "productionUnit": "kW"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUILDING_NOT_FOUND"));
    }

    @Test
    void changeProduction_deviceNotFound_returns404() throws Exception {
        willThrow(new DeviceNotFoundException()).given(appService).changeProduction(any(), any(), any());

        mockMvc.perform(patch("/v1/buildings/{buildingId}/devices/{deviceId}/production",
                        BUILDING_ID, DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productionValue": 60, "productionUnit": "kW"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("DEVICE_NOT_FOUND"));
    }

    // =====================================================================
    // Conflict path: invalid requests fail with 409 (GlobalExceptionHandler.handleBusinessRule()
    // =====================================================================

    @Test
    void addDevice_duplicateDevice_returns409() throws Exception {
        willThrow(new DeviceAlreadyExistsException()).given(appService).addDevice(any());

        mockMvc.perform(post("/v1/buildings/{id}/devices", BUILDING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "SOLAR", "ratedCapacityValue": 100, "ratedCapacityUnit": "kW"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DEVICE_ALREADY_EXISTS"));
    }

    @Test
    void changeConsumption_exceedsCapacity_returns409() throws Exception {
        willThrow(new BuildingTotalCapacityExceededException()).given(appService).changeConsumption(any(), any());

        mockMvc.perform(patch("/v1/buildings/{id}/consumption", BUILDING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consumptionValue": 999, "consumptionUnit": "kW"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TOTAL_CAPACITY_EXCEEDED"));
    }

    @Test
    void changeProduction_exceedsDeviceCapacity_returns409() throws Exception {
        willThrow(new DeviceCapacityLimitException()).given(appService).changeProduction(any(), any(), any());

        mockMvc.perform(patch("/v1/buildings/{buildingId}/devices/{deviceId}/production",
                        BUILDING_ID, DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productionValue": 999, "productionUnit": "kW"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DEVICE_CAPACITY_OUT_OF_RANGE"));
    }

    @Test
    void optimisticLock_returns409WithConcurrentModification() throws Exception {
        willThrow(new ObjectOptimisticLockingFailureException("PublicBuildingJpaEntity", BUILDING_ID))
                .given(appService).addDevice(any());

        mockMvc.perform(post("/v1/buildings/{id}/devices", BUILDING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "SOLAR", "ratedCapacityValue": 100, "ratedCapacityUnit": "kW"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONCURRENT_MODIFICATION"));
    }

    // =====================================================================
    // Error path: unexpected exceptions fail with 500 (GlobalExceptionHandler.handleUnknown())
    // =====================================================================

    @Test
    void unexpectedException_returns500() throws Exception {
        given(appService.create(any())).willThrow(new RuntimeException("Unexpected"));

        mockMvc.perform(post("/v1/buildings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "City Hall", "location": "Main St 1"}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"));
    }

    // =====================================================================
    // ErrorResponse shape contract
    // =====================================================================

    @Test
    void errorResponse_hasAllRequiredFields() throws Exception {
        mockMvc.perform(post("/v1/buildings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": ""}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.requestId").exists());
    }
}
