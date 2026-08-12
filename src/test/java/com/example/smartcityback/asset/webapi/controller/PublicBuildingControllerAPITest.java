package com.example.smartcityback.asset.webapi.controller;

import com.example.smartcityback.asset.application.dto.EnergyDeviceDto;
import com.example.smartcityback.asset.application.dto.PublicBuildingDto;
import com.example.smartcityback.asset.application.exception.BuildingNotFoundException;
import com.example.smartcityback.asset.application.service.PublicBuildingAppService;
import com.example.smartcityback.asset.domain.exception.BuildingAlreadyExistsException;
import com.example.smartcityback.asset.domain.exception.BuildingTotalCapacityExceededException;
import com.example.smartcityback.asset.domain.exception.DeviceCapacityLimitException;
import com.example.smartcityback.asset.domain.exception.DeviceAlreadyExistsException;
import com.example.smartcityback.asset.domain.exception.DeviceNotFoundException;
import com.example.smartcityback.asset.domain.exception.ValidationException;
import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;
import com.example.smartcityback.asset.domain.shared.enums.ErrorCode;
import com.example.smartcityback.asset.application.service.PublicBuildingQueryService;
import com.example.smartcityback.auth.infrastructure.jwt.JwtTokenService;
import com.example.smartcityback.auth.infrastructure.token.TokenBlacklist;
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("creates a building and returns 201 with a Location header and a body that both point at the new ID")
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
    @DisplayName("adds a device to a building and returns 204 with no body")
    void addDevice_validRequest_returns204() throws Exception {
        mockMvc.perform(post("/v1/buildings/{id}/devices", BUILDING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Solar Panel", "type": "SOLAR", "ratedCapacityValue": 100, "ratedCapacityUnit": "kW", "version": 0}
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("changes a building's consumption and returns 204 with no body")
    void changeConsumption_validRequest_returns204() throws Exception {
        mockMvc.perform(patch("/v1/buildings/{id}/consumption", BUILDING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consumptionValue": 50, "consumptionUnit": "kW", "version": 0}
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("returns 200 with the paginated result's content, total elements, total pages, page number, "
            + "and page size all present in the body")
    void getAll_returns200WithPagedResult() throws Exception {
        given(queryService.getAll(any(), any(), anyInt(), anyInt(), anyString(), anyString()))
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
    @DisplayName("splits a \"name,desc\" sort query parameter into separate field and direction arguments "
            + "passed to the service")
    void getAll_sortDesc_passesDescDirectionToService() throws Exception {
        given(queryService.getAll(any(), any(), anyInt(), anyInt(), anyString(), anyString()))
                .willReturn(new com.example.smartcityback.asset.shared.PagedResult<>(List.of(), 0L, 0, 0, 20));

        mockMvc.perform(get("/v1/buildings").param("sort", "name,desc"))
                .andExpect(status().isOk());

        then(queryService).should().getAll(any(), any(), anyInt(), anyInt(), eq("name"), eq("desc"));
    }

    @Test
    @DisplayName("splits a \"name,asc\" sort query parameter into separate field and direction arguments "
            + "passed to the service")
    void getAll_sortAsc_passesAscDirectionToService() throws Exception {
        given(queryService.getAll(any(), any(), anyInt(), anyInt(), anyString(), anyString()))
                .willReturn(new com.example.smartcityback.asset.shared.PagedResult<>(List.of(), 0L, 0, 0, 20));

        mockMvc.perform(get("/v1/buildings").param("sort", "name,asc"))
                .andExpect(status().isOk());

        then(queryService).should().getAll(any(), any(), anyInt(), anyInt(), eq("name"), eq("asc"));
    }

    @Test
    @DisplayName("routes to the subsidy-eligibility query, not the regular listing query, when eligible=true")
    void getAll_eligibleTrue_callsGetEligibleForSubsidyNotGetAll() throws Exception {
        given(queryService.getEligibleForSubsidy(anyInt(), anyInt(), anyString(), anyString()))
                .willReturn(new com.example.smartcityback.asset.shared.PagedResult<>(List.of(), 0L, 0, 0, 20));

        mockMvc.perform(get("/v1/buildings").param("eligible", "true"))
                .andExpect(status().isOk());

        then(queryService).should().getEligibleForSubsidy(anyInt(), anyInt(), anyString(), anyString());
        then(queryService).should(never()).getAll(any(), any(), anyInt(), anyInt(), anyString(), anyString());
    }

    @Test
    @DisplayName("routes to the regular listing query, not the subsidy-eligibility query, when eligible=false")
    void getAll_eligibleFalse_callsGetAllNotGetEligibleForSubsidy() throws Exception {
        given(queryService.getAll(any(), any(), anyInt(), anyInt(), anyString(), anyString()))
                .willReturn(new com.example.smartcityback.asset.shared.PagedResult<>(List.of(), 0L, 0, 0, 20));

        mockMvc.perform(get("/v1/buildings").param("eligible", "false"))
                .andExpect(status().isOk());

        then(queryService).should().getAll(any(), any(), anyInt(), anyInt(), anyString(), anyString());
        then(queryService).should(never()).getEligibleForSubsidy(anyInt(), anyInt(), anyString(), anyString());
    }

    @Test
    @DisplayName("routes to the regular listing query by default when the eligible parameter is omitted entirely")
    void getAll_eligibleParamAbsent_callsGetAll() throws Exception {
        given(queryService.getAll(any(), any(), anyInt(), anyInt(), anyString(), anyString()))
                .willReturn(new com.example.smartcityback.asset.shared.PagedResult<>(List.of(), 0L, 0, 0, 20));

        mockMvc.perform(get("/v1/buildings"))
                .andExpect(status().isOk());

        then(queryService).should().getAll(any(), any(), anyInt(), anyInt(), anyString(), anyString());
        then(queryService).should(never()).getEligibleForSubsidy(anyInt(), anyInt(), anyString(), anyString());
    }

    @Test
    @DisplayName("forwards the name filter to the service while leaving the location filter null")
    void getAll_nameParam_forwardedToService() throws Exception {
        given(queryService.getAll(any(), any(), anyInt(), anyInt(), anyString(), anyString()))
                .willReturn(new com.example.smartcityback.asset.shared.PagedResult<>(List.of(), 0L, 0, 0, 20));

        mockMvc.perform(get("/v1/buildings").param("name", "City"))
                .andExpect(status().isOk());

        then(queryService).should().getAll(eq("City"), isNull(), anyInt(), anyInt(), anyString(), anyString());
    }

    @Test
    @DisplayName("forwards the location filter to the service while leaving the name filter null")
    void getAll_locationParam_forwardedToService() throws Exception {
        given(queryService.getAll(any(), any(), anyInt(), anyInt(), anyString(), anyString()))
                .willReturn(new com.example.smartcityback.asset.shared.PagedResult<>(List.of(), 0L, 0, 0, 20));

        mockMvc.perform(get("/v1/buildings").param("location", "Main St"))
                .andExpect(status().isOk());

        then(queryService).should().getAll(isNull(), eq("Main St"), anyInt(), anyInt(), anyString(), anyString());
    }

    @Test
    @DisplayName("forwards both the name and location filters to the service together")
    void getAll_nameAndLocationParams_bothForwardedToService() throws Exception {
        given(queryService.getAll(any(), any(), anyInt(), anyInt(), anyString(), anyString()))
                .willReturn(new com.example.smartcityback.asset.shared.PagedResult<>(List.of(), 0L, 0, 0, 20));

        mockMvc.perform(get("/v1/buildings").param("name", "City").param("location", "Main St"))
                .andExpect(status().isOk());

        then(queryService).should().getAll(eq("City"), eq("Main St"), anyInt(), anyInt(), anyString(), anyString());
    }

    @Test
    @DisplayName("returns 200 with a building's ID, name, location, device list, and version all in the body")
    void getBuilding_found_returns200WithBody() throws Exception {
        PublicBuildingDto response = new PublicBuildingDto(
                BUILDING_ID, "City Hall", "Main St 1",
                BigDecimal.ZERO, EnergyUnit.kW,
                List.of(new EnergyDeviceDto(DEVICE_ID,
                        "Solar Panel",
                        com.example.smartcityback.asset.domain.shared.enums.DeviceType.SOLAR,
                        new BigDecimal("100"), EnergyUnit.kW,
                        BigDecimal.ZERO, EnergyUnit.kW)),
                3L
        );
        given(queryService.getById(BUILDING_ID)).willReturn(response);

        mockMvc.perform(get("/v1/buildings/{id}", BUILDING_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(BUILDING_ID.toString()))
                .andExpect(jsonPath("$.name").value("City Hall"))
                .andExpect(jsonPath("$.location").value("Main St 1"))
                .andExpect(jsonPath("$.devices").isArray())
                .andExpect(jsonPath("$.devices[0].name").value("Solar Panel"))
                .andExpect(jsonPath("$.devices[0].type").value("SOLAR"))
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    @DisplayName("returns 404 with a BUILDING_NOT_FOUND error code for a building ID that doesn't exist")
    void getBuilding_notFound_returns404() throws Exception {
        given(queryService.getById(BUILDING_ID)).willThrow(new BuildingNotFoundException());

        mockMvc.perform(get("/v1/buildings/{id}", BUILDING_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUILDING_NOT_FOUND"));
    }

    @Test
    @DisplayName("deletes a building and returns 204 with no body")
    void delete_validRequest_returns204() throws Exception {
        mockMvc.perform(delete("/v1/buildings/{id}", BUILDING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": 0}
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("removes a device from a building and returns 204 with no body")
    void removeDevice_validRequest_returns204() throws Exception {
        mockMvc.perform(delete("/v1/buildings/{buildingId}/devices/{deviceId}", BUILDING_ID, DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": 0}
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("changes a device's production and returns 204 with no body")
    void changeProduction_validRequest_returns204() throws Exception {
        mockMvc.perform(patch("/v1/buildings/{buildingId}/devices/{deviceId}/production",
                        BUILDING_ID, DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productionValue": 60, "productionUnit": "kW", "version": 0}
                                """))
                .andExpect(status().isNoContent());
    }

    // =====================================================================
    // Validation path: invalid requests fail with 422 (GlobalExceptionHandler.handleValidation())
    // =====================================================================

    @Test
    @DisplayName("translates a domain-level ValidationException into a 422 response carrying that exception's "
            + "own specific error code (BUILDING_NAME_EMPTY), not a generic one")
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
    @DisplayName("rejects a blank building name at the request-validation layer, before the domain is ever "
            + "reached, with a generic VALIDATION_ERROR code")
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
    @DisplayName("rejects a create-building request with no location field at all")
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
    @DisplayName("rejects an add-device request with no device type")
    void addDevice_missingType_returns422() throws Exception {
        mockMvc.perform(post("/v1/buildings/{id}/devices", BUILDING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Solar Panel", "ratedCapacityValue": 50, "ratedCapacityUnit": "kW"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("rejects an add-device request with a blank device name")
    void addDevice_blankName_returns422() throws Exception {
        mockMvc.perform(post("/v1/buildings/{id}/devices", BUILDING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "type": "SOLAR", "ratedCapacityValue": 100, "ratedCapacityUnit": "kW"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("rejects an add-device request with a negative rated capacity")
    void addDevice_negativeCapacity_returns422() throws Exception {
        mockMvc.perform(post("/v1/buildings/{id}/devices", BUILDING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Solar Panel", "type": "SOLAR", "ratedCapacityValue": -5, "ratedCapacityUnit": "kW"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("rejects a consumption-change request with a negative value")
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
    @DisplayName("rejects a production-change request with a negative value")
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

    @Test
    @DisplayName("rejects an add-device request with no version field, since optimistic locking requires the "
            + "client to state what version it expects")
    void addDevice_missingVersion_returns422() throws Exception {
        mockMvc.perform(post("/v1/buildings/{id}/devices", BUILDING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Solar Panel", "type": "SOLAR", "ratedCapacityValue": 100, "ratedCapacityUnit": "kW"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("rejects a consumption-change request with no version field")
    void changeConsumption_missingVersion_returns422() throws Exception {
        mockMvc.perform(patch("/v1/buildings/{id}/consumption", BUILDING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consumptionValue": 50, "consumptionUnit": "kW"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("rejects a production-change request with no version field")
    void changeProduction_missingVersion_returns422() throws Exception {
        mockMvc.perform(patch("/v1/buildings/{buildingId}/devices/{deviceId}/production",
                        BUILDING_ID, DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productionValue": 60, "productionUnit": "kW"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("rejects a remove-device request with no version field")
    void removeDevice_missingVersion_returns422() throws Exception {
        mockMvc.perform(delete("/v1/buildings/{buildingId}/devices/{deviceId}", BUILDING_ID, DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    // =====================================================================
    // Not Found path: invalid requests fail with 404 (GlobalExceptionHandler.handleNotFound())
    // =====================================================================

    @Test
    @DisplayName("translates the service throwing BuildingNotFoundException during addDevice into a 404 with "
            + "a BUILDING_NOT_FOUND error code")
    void addDevice_buildingNotFound_returns404() throws Exception {
        willThrow(new BuildingNotFoundException()).given(appService).addDevice(any());

        mockMvc.perform(post("/v1/buildings/{id}/devices", BUILDING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Solar Panel", "type": "SOLAR", "ratedCapacityValue": 100, "ratedCapacityUnit": "kW", "version": 0}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUILDING_NOT_FOUND"));
    }

    @Test
    @DisplayName("translates the service throwing BuildingNotFoundException during changeConsumption into a "
            + "404 with a BUILDING_NOT_FOUND error code")
    void changeConsumption_buildingNotFound_returns404() throws Exception {
        willThrow(new BuildingNotFoundException()).given(appService).changeConsumption(any(), any());

        mockMvc.perform(patch("/v1/buildings/{id}/consumption", BUILDING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consumptionValue": 50, "consumptionUnit": "kW", "version": 0}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUILDING_NOT_FOUND"));
    }

    @Test
    @DisplayName("translates the service throwing BuildingNotFoundException during changeProduction into a "
            + "404 with a BUILDING_NOT_FOUND error code")
    void changeProduction_buildingNotFound_returns404() throws Exception {
        willThrow(new BuildingNotFoundException()).given(appService).changeProduction(any(), any(), any());

        mockMvc.perform(patch("/v1/buildings/{buildingId}/devices/{deviceId}/production",
                        BUILDING_ID, DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productionValue": 60, "productionUnit": "kW", "version": 0}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUILDING_NOT_FOUND"));
    }

    @Test
    @DisplayName("translates the service throwing DeviceNotFoundException during changeProduction into a 404 "
            + "with a DEVICE_NOT_FOUND error code, distinct from the building-not-found case")
    void changeProduction_deviceNotFound_returns404() throws Exception {
        willThrow(new DeviceNotFoundException()).given(appService).changeProduction(any(), any(), any());

        mockMvc.perform(patch("/v1/buildings/{buildingId}/devices/{deviceId}/production",
                        BUILDING_ID, DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productionValue": 60, "productionUnit": "kW", "version": 0}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("DEVICE_NOT_FOUND"));
    }

    @Test
    @DisplayName("translates the service throwing BuildingNotFoundException during delete into a 404 with a "
            + "BUILDING_NOT_FOUND error code")
    void delete_buildingNotFound_returns404() throws Exception {
        willThrow(new BuildingNotFoundException()).given(appService).delete(any(), any());

        mockMvc.perform(delete("/v1/buildings/{id}", BUILDING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": 0}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUILDING_NOT_FOUND"));
    }

    @Test
    @DisplayName("rejects a delete request with no version field")
    void delete_missingVersion_returns422() throws Exception {
        mockMvc.perform(delete("/v1/buildings/{id}", BUILDING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("translates the service throwing BuildingNotFoundException during removeDevice into a 404 "
            + "with a BUILDING_NOT_FOUND error code")
    void removeDevice_buildingNotFound_returns404() throws Exception {
        willThrow(new BuildingNotFoundException()).given(appService).removeDevice(any(), any(), any());

        mockMvc.perform(delete("/v1/buildings/{buildingId}/devices/{deviceId}", BUILDING_ID, DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": 0}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUILDING_NOT_FOUND"));
    }

    @Test
    @DisplayName("translates the service throwing DeviceNotFoundException during removeDevice into a 404 "
            + "with a DEVICE_NOT_FOUND error code")
    void removeDevice_deviceNotFound_returns404() throws Exception {
        willThrow(new DeviceNotFoundException()).given(appService).removeDevice(any(), any(), any());

        mockMvc.perform(delete("/v1/buildings/{buildingId}/devices/{deviceId}", BUILDING_ID, DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": 0}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("DEVICE_NOT_FOUND"));
    }

    // =====================================================================
    // Conflict path: invalid requests fail with 409 (GlobalExceptionHandler.handleBusinessRule()
    // =====================================================================

    @Test
    @DisplayName("translates the service throwing DeviceAlreadyExistsException into a 409 with a "
            + "DEVICE_ALREADY_EXISTS error code")
    void addDevice_duplicateDevice_returns409() throws Exception {
        willThrow(new DeviceAlreadyExistsException()).given(appService).addDevice(any());

        mockMvc.perform(post("/v1/buildings/{id}/devices", BUILDING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Solar Panel", "type": "SOLAR", "ratedCapacityValue": 100, "ratedCapacityUnit": "kW", "version": 0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DEVICE_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("translates the service throwing BuildingAlreadyExistsException into a 409 with a "
            + "BUILDING_ALREADY_EXISTS error code")
    void create_duplicateNameAndLocation_returns409() throws Exception {
        willThrow(new BuildingAlreadyExistsException()).given(appService).create(any());

        mockMvc.perform(post("/v1/buildings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "City Hall", "location": "Main St 1"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("BUILDING_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("translates the service throwing BuildingTotalCapacityExceededException into a 409 with a "
            + "TOTAL_CAPACITY_EXCEEDED error code")
    void changeConsumption_exceedsCapacity_returns409() throws Exception {
        willThrow(new BuildingTotalCapacityExceededException()).given(appService).changeConsumption(any(), any());

        mockMvc.perform(patch("/v1/buildings/{id}/consumption", BUILDING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consumptionValue": 999, "consumptionUnit": "kW", "version": 0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TOTAL_CAPACITY_EXCEEDED"));
    }

    @Test
    @DisplayName("translates the service throwing DeviceCapacityLimitException into a 409 with a "
            + "DEVICE_CAPACITY_OUT_OF_RANGE error code")
    void changeProduction_exceedsDeviceCapacity_returns409() throws Exception {
        willThrow(new DeviceCapacityLimitException()).given(appService).changeProduction(any(), any(), any());

        mockMvc.perform(patch("/v1/buildings/{buildingId}/devices/{deviceId}/production",
                        BUILDING_ID, DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productionValue": 999, "productionUnit": "kW", "version": 0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DEVICE_CAPACITY_OUT_OF_RANGE"));
    }

    @Test
    @DisplayName("translates the service throwing ObjectOptimisticLockingFailureException into a 409 with a "
            + "CONCURRENT_MODIFICATION error code, instead of the generic 500 an unmapped exception would get")
    void optimisticLock_returns409WithConcurrentModification() throws Exception {
        willThrow(new ObjectOptimisticLockingFailureException("PublicBuildingJpaEntity", BUILDING_ID))
                .given(appService).addDevice(any());

        mockMvc.perform(post("/v1/buildings/{id}/devices", BUILDING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Solar Panel", "type": "SOLAR", "ratedCapacityValue": 100, "ratedCapacityUnit": "kW", "version": 0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONCURRENT_MODIFICATION"));
    }

    // =====================================================================
    // Error path: unexpected exceptions fail with 500 (GlobalExceptionHandler.handleUnknown())
    // =====================================================================

    @Test
    @DisplayName("translates a completely unexpected, unmapped exception into a 500 with an INTERNAL_ERROR "
            + "error code, instead of leaking a stack trace or crashing the response")
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
    @DisplayName("includes an error code, message, HTTP status, timestamp, and request ID in every error "
            + "response, regardless of which specific error triggered it")
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
