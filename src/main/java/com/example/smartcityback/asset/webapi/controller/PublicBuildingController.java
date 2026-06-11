package com.example.smartcityback.asset.webapi.controller;

import com.example.smartcityback.asset.application.command.AddDeviceCommand;
import com.example.smartcityback.asset.application.command.ChangeConsumptionCommand;
import com.example.smartcityback.asset.application.command.ChangeProductionCommand;
import com.example.smartcityback.asset.application.command.CreateBuildingCommand;
import com.example.smartcityback.asset.application.service.PublicBuildingAppService;
import com.example.smartcityback.asset.application.service.PublicBuildingQueryService;
import com.example.smartcityback.asset.webapi.exception.ErrorResponse;
import com.example.smartcityback.asset.webapi.mapper.BuildingResponseMapper;
import com.example.smartcityback.asset.webapi.request.AddDeviceRequest;
import com.example.smartcityback.asset.webapi.request.ChangeConsumptionRequest;
import com.example.smartcityback.asset.webapi.request.ChangeProductionRequest;
import com.example.smartcityback.asset.webapi.request.CreateBuildingRequest;
import com.example.smartcityback.asset.webapi.response.PublicBuildingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@CrossOrigin(origins = {"http://localhost:4200", "https://zaricu22.github.io"})
@RestController
@RequestMapping("/v1/buildings")
@Slf4j
@Tag(name = "Public Buildings")
public class PublicBuildingController {

    private final PublicBuildingAppService service;
    private final PublicBuildingQueryService queryService;

    public PublicBuildingController(PublicBuildingAppService service,
                                    PublicBuildingQueryService queryService) {
        this.service = service;
        this.queryService = queryService;
    }

    private String requestId() {
        return MDC.get("requestId");
    }

    @Operation(summary = "Create a new public building")
    @ApiResponses({
            @ApiResponse(responseCode = "201"),
            @ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<UUID> create(
            @Valid @RequestBody CreateBuildingRequest request) {

        UUID id = service.create(new CreateBuildingCommand(request.name(), request.location()));
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();
        return ResponseEntity.created(location).body(id);
    }

    @Operation(summary = "Add an energy device to a building")
    @ApiResponses({
            @ApiResponse(responseCode = "204"),
            @ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/devices")
    public ResponseEntity<Void> addDevice(
            @PathVariable UUID id,
            @Valid @RequestBody AddDeviceRequest request) {

        log.info("RequestReceived endpoint=POST /buildings/{id}/devices id={} type={} capacity={} {} requestId={}",
                id, request.type(), request.ratedCapacityValue(), request.ratedCapacityUnit(), requestId());

        service.addDevice(new AddDeviceCommand(
                id,
                request.type(),
                request.ratedCapacityValue(),
                request.ratedCapacityUnit()
        ));

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Update energy consumption of a building")
    @ApiResponses({
            @ApiResponse(responseCode = "204"),
            @ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/consumption")
    public ResponseEntity<Void> changeConsumption(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeConsumptionRequest request) {

        log.info("RequestReceived endpoint=/buildings/{id}/consumption id={} unit={} value={} requestId={}",
                id, request.consumptionUnit(), request.consumptionValue(), requestId());

        service.changeConsumption(
                id,
                new ChangeConsumptionCommand(
                        request.consumptionValue(),
                        request.consumptionUnit()
                )
        );

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Update production rate of a device")
    @ApiResponses({
            @ApiResponse(responseCode = "204"),
            @ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{buildingId}/devices/{deviceId}/production")
    public ResponseEntity<Void> changeProduction(
            @PathVariable UUID buildingId,
            @PathVariable UUID deviceId,
            @Valid @RequestBody ChangeProductionRequest request) {

        log.info("RequestReceived endpoint=PATCH /buildings/{buildingId}/devices/{deviceId}/production buildingId={} deviceId={} value={} {} requestId={}",
                buildingId, deviceId, request.productionValue(), request.productionUnit(), requestId());

        service.changeProduction(
                buildingId,
                deviceId,
                new ChangeProductionCommand(
                        request.productionValue(),
                        request.productionUnit()
                )
        );

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get a public building by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200"),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public PublicBuildingResponse get(
            @PathVariable UUID id) {
        return BuildingResponseMapper.toResponse(queryService.getById(id));
    }

    @Operation(summary = "Get all public buildings")
    @ApiResponse(responseCode = "200")
    @GetMapping
    public List<PublicBuildingResponse> getAll() {
        return BuildingResponseMapper.toResponseList(queryService.getAll());
    }
}
