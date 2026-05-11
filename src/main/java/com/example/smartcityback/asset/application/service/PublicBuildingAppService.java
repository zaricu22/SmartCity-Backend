package com.example.smartcityback.asset.application.service;

import com.example.smartcityback.asset.application.command.AddDeviceCommand;
import com.example.smartcityback.asset.application.command.ChangeConsumptionCommand;
import com.example.smartcityback.asset.application.command.ChangeProductionCommand;
import com.example.smartcityback.asset.application.command.CreateBuildingCommand;
import com.example.smartcityback.asset.domain.aggregate.PublicBuilding;
import com.example.smartcityback.asset.domain.entity.EnergyDevice;
import com.example.smartcityback.asset.application.exception.BuildingNotFoundException;
import com.example.smartcityback.asset.domain.repository.PublicBuildingRepository;
import com.example.smartcityback.asset.domain.valueobject.Energy;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Transactional
@Slf4j
public class PublicBuildingAppService {

    private final PublicBuildingRepository repository;

    // Spring's built-in event publisher for decoupled communication between components (e.g., for domain events)
    private final ApplicationEventPublisher eventPublisher;

    // private final SubsidyEligibilitySpecification subsidySpec;

    public PublicBuildingAppService(PublicBuildingRepository repository,
                                    ApplicationEventPublisher eventPublisher
            /*, SubsidyEligibilitySpecification subsidySpec*/) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    public UUID create(CreateBuildingCommand cmd) {
        PublicBuilding building = new PublicBuilding(UUID.randomUUID(), cmd.name(), cmd.location());

        repository.save(building);
        return building.getId();
    }

    public void addDevice(AddDeviceCommand cmd) {
        PublicBuilding building = repository.findById(cmd.buildingId())
                .orElseThrow(BuildingNotFoundException::new);

        building.addDevice(
                new EnergyDevice(
                        UUID.randomUUID(), cmd.type(),
                        new Energy(cmd.ratedCapacityValue(),cmd.ratedCapacityUnit())
                )
        );

        repository.save(building);
        building.pullEvents().forEach(eventPublisher::publishEvent);

        // NOTE: DDD Specification pattern can be used here
        //
        //   if (subsidySpec.isSatisfiedBy(building)) {
        //       subsidyService.apply(building);
        //       authorityNotificationService.notify(building);
        //   }
        //
        // See: com.example.smartcityback.asset.domain.specification.SubsidyEligibilitySpecification
    }

    public void changeConsumption(UUID buildingId, ChangeConsumptionCommand cmd) {
        log.info("CommandReceived command=ChangeConsumption aggregate=PublicBuilding buildingId={} unit={} value={}",
                buildingId,
                cmd.consumptionUnit(),
                cmd.consumptionValue());

        PublicBuilding building = repository.findById(buildingId)
                .orElseThrow(() -> {
                    log.warn("BuildingNotFound buildingId={}", buildingId);
                    return new BuildingNotFoundException();
                });

        log.info("ChangingConsumption buildingId={} oldValue={} newValue={}",
                buildingId,
                building.getConsumption(),
                cmd.consumptionValue());

        building.changeConsumption(
                new Energy(cmd.consumptionValue(), cmd.consumptionUnit())
        );

        repository.save(building);
        building.pullEvents().forEach(eventPublisher::publishEvent);

        log.info("ConsumptionChanged buildingId={}", buildingId);
    }

    public void changeProduction(UUID buildingId, UUID deviceId, ChangeProductionCommand cmd) {
        PublicBuilding building = repository.findById(buildingId)
                .orElseThrow(BuildingNotFoundException::new);

        Energy production = new Energy(cmd.productionValue(), cmd.productionUnit());

        building.changeDeviceProduction(deviceId, production);

        repository.save(building);
    }
}
