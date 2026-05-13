package com.example.smartcityback.asset.webapi.controller;

import com.example.smartcityback.asset.application.command.AddDeviceCommand;
import com.example.smartcityback.asset.application.command.ChangeConsumptionCommand;
import com.example.smartcityback.asset.application.command.ChangeProductionCommand;
import com.example.smartcityback.asset.application.command.CreateBuildingCommand;
import com.example.smartcityback.asset.application.service.PublicBuildingAppService;
import com.example.smartcityback.asset.application.service.PublicBuildingQueryService;
import com.example.smartcityback.asset.webapi.mapper.BuildingResponseMapper;
import com.example.smartcityback.asset.webapi.request.AddDeviceRequest;
import com.example.smartcityback.asset.webapi.request.ChangeConsumptionRequest;
import com.example.smartcityback.asset.webapi.request.ChangeProductionRequest;
import com.example.smartcityback.asset.webapi.request.CreateBuildingRequest;
import com.example.smartcityback.asset.webapi.response.PublicBuildingResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@CrossOrigin(origins = {"http://localhost:4200", "https://zaricu22.github.io"})
@RestController
@RequestMapping("/v1/buildings")
@Slf4j
// @RequiredArgsConstructor
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

    @PostMapping
    public ResponseEntity<UUID> create(
            @Valid @RequestBody CreateBuildingRequest request) {

        UUID id = service.create(new CreateBuildingCommand(request.name(), request.location()));
        return ResponseEntity.ok(id);
    }

    @PostMapping("/{id}/devices")
    public ResponseEntity<Void> addDevice(
            @PathVariable UUID id,
            @Valid @RequestBody AddDeviceRequest request) {

        service.addDevice(new AddDeviceCommand(
                id,
                request.type(),
                request.ratedCapacityValue(),
                request.ratedCapacityUnit()
        ));

        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/consumption")
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

        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{buildingId}/devices/{deviceId}/production")
    public ResponseEntity<Void> changeProduction(
            @PathVariable UUID buildingId,
            @PathVariable UUID deviceId,
            @Valid @RequestBody ChangeProductionRequest request) {

        service.changeProduction(
                buildingId,
                deviceId,
                new ChangeProductionCommand(
                        request.productionValue(),
                        request.productionUnit()
                )
        );

        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    public PublicBuildingResponse get(
            @PathVariable UUID id) {
        return BuildingResponseMapper.toResponse(queryService.getById(id));
    }

    @GetMapping("/all")
    public List<PublicBuildingResponse> getAll() {
        return BuildingResponseMapper.toResponseList(queryService.getAll());
    }
}
