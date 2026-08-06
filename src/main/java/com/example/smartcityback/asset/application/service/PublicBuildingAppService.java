package com.example.smartcityback.asset.application.service;

import com.example.smartcityback.asset.application.command.AddDeviceCommand;
import com.example.smartcityback.asset.application.command.ChangeConsumptionCommand;
import com.example.smartcityback.asset.application.command.ChangeProductionCommand;
import com.example.smartcityback.asset.application.command.CreateBuildingCommand;
import com.example.smartcityback.asset.domain.aggregate.PublicBuilding;
import com.example.smartcityback.asset.domain.entity.EnergyDevice;
import com.example.smartcityback.asset.domain.event.BuildingDeletedEvent;
import com.example.smartcityback.asset.application.exception.BuildingNotFoundException;
import com.example.smartcityback.asset.domain.repository.PublicBuildingRepository;
import com.example.smartcityback.asset.domain.valueobject.Energy;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.Objects;
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

    // Guards against stale edits: a client submits the version it last read, and this compares
    // that against the version on the aggregate just loaded from the DB. A mismatch means
    // someone else changed the building since the client read it, so we reject before mutating
    // rather than relying solely on Hibernate's flush-time @Version check (which only catches
    // requests racing within the same instant, not edits based on stale data).
    private void checkVersion(PublicBuilding building, Long expectedVersion) {
        if (!Objects.equals(building.getVersion(), expectedVersion)) {
            log.warn("OptimisticLockConflict buildingId={} expectedVersion={} actualVersion={}",
                    building.getId(), expectedVersion, building.getVersion());
            throw new ObjectOptimisticLockingFailureException(PublicBuilding.class, building.getId());
        }
    }

    public UUID create(CreateBuildingCommand cmd) {
        PublicBuilding building = new PublicBuilding(UUID.randomUUID(), cmd.name(), cmd.location());

        repository.save(building);
        building.pullEvents().forEach(eventPublisher::publishEvent);

        return building.getId();
    }

    public void addDevice(AddDeviceCommand cmd) {
        log.info("CommandReceived command=AddDevice aggregate=PublicBuilding buildingId={} type={} capacity={} {}",
                cmd.buildingId(), cmd.type(), cmd.ratedCapacityValue(), cmd.ratedCapacityUnit());

        PublicBuilding building = repository.findById(cmd.buildingId())
                .orElseThrow(() -> {
                    log.warn("BuildingNotFound buildingId={}", cmd.buildingId());
                    return new BuildingNotFoundException();
                });

        checkVersion(building, cmd.version());

        building.addDevice(
                new EnergyDevice(
                        UUID.randomUUID(), cmd.name(), cmd.type(),
                        new Energy(cmd.ratedCapacityValue(),cmd.ratedCapacityUnit())
                )
        );

        repository.save(building);
        building.pullEvents().forEach(eventPublisher::publishEvent);

        log.info("DeviceAdded buildingId={}", cmd.buildingId());

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

        checkVersion(building, cmd.version());

        building.changeConsumption(
                new Energy(cmd.consumptionValue(), cmd.consumptionUnit())
        );

        repository.save(building);
        building.pullEvents().forEach(eventPublisher::publishEvent);

        log.info("ConsumptionChanged buildingId={}", buildingId);
    }

    public void delete(UUID buildingId, Long version) {
        log.info("CommandReceived command=DeleteBuilding aggregate=PublicBuilding buildingId={}", buildingId);

        PublicBuilding building = repository.findById(buildingId)
                .orElseThrow(() -> {
                    log.warn("BuildingNotFound buildingId={}", buildingId);
                    return new BuildingNotFoundException();
                });

        checkVersion(building, version);

        repository.delete(buildingId);
        eventPublisher.publishEvent(new BuildingDeletedEvent(buildingId, building.getName()));

        log.info("BuildingDeleted buildingId={}", buildingId);
    }

    public void removeDevice(UUID buildingId, UUID deviceId, Long version) {
        log.info("CommandReceived command=RemoveDevice aggregate=PublicBuilding buildingId={} deviceId={}",
                buildingId, deviceId);

        PublicBuilding building = repository.findById(buildingId)
                .orElseThrow(() -> {
                    log.warn("BuildingNotFound buildingId={}", buildingId);
                    return new BuildingNotFoundException();
                });

        checkVersion(building, version);

        building.removeDevice(deviceId);

        repository.save(building);
        building.pullEvents().forEach(eventPublisher::publishEvent);

        log.info("DeviceRemoved buildingId={} deviceId={}", buildingId, deviceId);
    }

    public void changeProduction(UUID buildingId, UUID deviceId, ChangeProductionCommand cmd) {
        log.info("CommandReceived command=ChangeProduction aggregate=PublicBuilding buildingId={} deviceId={} value={} {}",
                buildingId, deviceId, cmd.productionValue(), cmd.productionUnit());

        PublicBuilding building = repository.findById(buildingId)
                .orElseThrow(() -> {
                    log.warn("BuildingNotFound buildingId={}", buildingId);
                    return new BuildingNotFoundException();
                });

        checkVersion(building, cmd.version());

        Energy production = new Energy(cmd.productionValue(), cmd.productionUnit());

        building.changeDeviceProduction(deviceId, production);

        repository.save(building);
        building.pullEvents().forEach(eventPublisher::publishEvent);

        log.info("ProductionChanged buildingId={} deviceId={}", buildingId, deviceId);
    }
}
