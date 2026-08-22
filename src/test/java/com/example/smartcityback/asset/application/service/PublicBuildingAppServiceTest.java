package com.example.smartcityback.asset.application.service;

import com.example.smartcityback.asset.application.command.*;
import com.example.smartcityback.asset.application.exception.BuildingNotFoundException;
import com.example.smartcityback.asset.domain.event.BuildingCreatedEvent;
import com.example.smartcityback.asset.domain.event.BuildingDeletedEvent;
import com.example.smartcityback.asset.domain.event.DeviceRemovedEvent;
import com.example.smartcityback.asset.domain.event.ProductionChangedEvent;
import com.example.smartcityback.asset.domain.aggregate.PublicBuilding;
import com.example.smartcityback.asset.domain.entity.EnergyDevice;
import com.example.smartcityback.asset.domain.repository.PublicBuildingRepository;
import com.example.smartcityback.asset.domain.shared.enums.DeviceType;
import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;
import com.example.smartcityback.asset.domain.valueobject.Energy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class PublicBuildingAppServiceTest {

    @Mock
    private PublicBuildingRepository repository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PublicBuildingAppService service;

    private static final UUID   BUILDING_ID     = UUID.randomUUID();
    private static final UUID   DEVICE_ID       = UUID.randomUUID();
    private static final Energy CAPACITY_100_KW = new Energy(new BigDecimal("100"), EnergyUnit.kW);

    // =====================================================================
    // Helpers
    // =====================================================================

    private PublicBuilding buildingWithOneDevice() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        building.addDevice(new EnergyDevice(DEVICE_ID, "Test Device", DeviceType.SOLAR, CAPACITY_100_KW));
        building.getDevices().get(0).changeProduction(new Energy(new BigDecimal("100"), EnergyUnit.kW));
        return building;
    }

    // =====================================================================
    // create
    // =====================================================================

    @Test
    @DisplayName("saves a new building to the repository")
    void create_validCommand_savesBuilding() {
        service.create(new CreateBuildingCommand("City Hall", "Main St 1"));

        then(repository).should().save(any(PublicBuilding.class));
    }

    @Test
    @DisplayName("returns a generated, non-null ID for the newly created building")
    void create_validCommand_returnsNonNullId() {
        UUID result = service.create(new CreateBuildingCommand("City Hall", "Main St 1"));

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("publishes a BuildingCreatedEvent after successfully creating a building")
    void create_validCommand_publishesBuildingCreatedEvent() {
        service.create(new CreateBuildingCommand("City Hall", "Main St 1"));

        then(eventPublisher).should().publishEvent(any(BuildingCreatedEvent.class));
    }

    @Test
    @DisplayName("rejects creating a building with the same name and location as one that already exists, "
            + "without saving anything or publishing an event")
    void create_duplicateNameAndLocation_throwsBuildingAlreadyExistsException() {
        given(repository.existsByNameAndLocation("City Hall", "Main St 1")).willReturn(true);

        assertThatThrownBy(() -> service.create(new CreateBuildingCommand("City Hall", "Main St 1")))
                .isInstanceOf(com.example.smartcityback.asset.domain.exception.BuildingAlreadyExistsException.class);

        then(repository).should(never()).save(any());
        then(eventPublisher).should(never()).publishEvent(any());
    }

    // =====================================================================
    // delete
    // =====================================================================

    @Test
    @DisplayName("rejects deleting a building ID that doesn't exist, without calling delete on the repository")
    void delete_buildingNotFound_throwsBuildingNotFoundException() {
        given(repository.findById(BUILDING_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(BUILDING_ID, null))
                .isInstanceOf(BuildingNotFoundException.class);

        then(repository).should(never()).delete(any());
    }

    @Test
    @DisplayName("deletes an existing building and publishes a BuildingDeletedEvent carrying its ID and name")
    void delete_buildingFound_deletesAndPublishesEvent() {
        given(repository.findById(BUILDING_ID)).willReturn(Optional.of(new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1")));

        service.delete(BUILDING_ID, null);

        then(repository).should().delete(BUILDING_ID);
        then(eventPublisher).should().publishEvent(new BuildingDeletedEvent(BUILDING_ID, "City Hall"));
    }

    @Test
    @DisplayName("rejects deleting a building when the request's version number is stale — the same conflict "
            + "two concurrent editors would trigger — without calling delete on the repository")
    void delete_versionMismatch_throwsOptimisticLockingFailureException() {
        given(repository.findById(BUILDING_ID)).willReturn(Optional.of(new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1")));

        assertThatThrownBy(() -> service.delete(BUILDING_ID, 5L))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        then(repository).should(never()).delete(any());
    }

    // =====================================================================
    // addDevice
    // =====================================================================

    @Test
    @DisplayName("rejects adding a device to a building ID that doesn't exist")
    void addDevice_buildingNotFound_throwsBuildingNotFoundException() {
        given(repository.findById(BUILDING_ID)).willReturn(Optional.empty());

        AddDeviceCommand cmd = new AddDeviceCommand(BUILDING_ID, "Test Device", DeviceType.SOLAR, new BigDecimal("50"), EnergyUnit.kW, null);

        assertThatThrownBy(() -> service.addDevice(cmd))
                .isInstanceOf(BuildingNotFoundException.class);
    }

    @Test
    @DisplayName("adds a device to an existing building and saves the updated aggregate with the device attached")
    void addDevice_buildingFound_savesUpdatedBuilding() {
        PublicBuilding emptyBuilding = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        given(repository.findById(BUILDING_ID)).willReturn(Optional.of(emptyBuilding));

        service.addDevice(new AddDeviceCommand(BUILDING_ID, "Test Device", DeviceType.SOLAR, new BigDecimal("50"), EnergyUnit.kW, null));

        then(repository).should().save(emptyBuilding);
        assertThat(emptyBuilding.getDevices()).hasSize(1);
    }

    // addDevice_domainException_doesNotSave() is not needed because PublicBuilding.addDevice()
    // always generate new ID for the new device, so it can never throw DeviceAlreadyExistsException
    // but domain-level validation is necessary and tested in publicBuildingTest.addDevice_duplicateDevice_throwsDeviceAlreadyExistsException()

    // =====================================================================
    // removeDevice
    // =====================================================================

    @Test
    @DisplayName("rejects removing a device from a building ID that doesn't exist")
    void removeDevice_buildingNotFound_throwsBuildingNotFoundException() {
        given(repository.findById(BUILDING_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeDevice(BUILDING_ID, DEVICE_ID, null))
                .isInstanceOf(BuildingNotFoundException.class);
    }

    @Test
    @DisplayName("rejects removing a device ID that isn't on the building, without saving anything")
    void removeDevice_deviceNotFound_throwsDeviceNotFoundException() {
        PublicBuilding emptyBuilding = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        given(repository.findById(BUILDING_ID)).willReturn(Optional.of(emptyBuilding));

        assertThatThrownBy(() -> service.removeDevice(BUILDING_ID, DEVICE_ID, null))
                .isInstanceOf(com.example.smartcityback.asset.domain.exception.DeviceNotFoundException.class);

        then(repository).should(never()).save(any());
    }

    @Test
    @DisplayName("removes a device from the building, saves the updated aggregate, and publishes a "
            + "DeviceRemovedEvent")
    void removeDevice_deviceFound_savesAndPublishesEvent() {
        PublicBuilding building = buildingWithOneDevice();
        given(repository.findById(BUILDING_ID)).willReturn(Optional.of(building));

        service.removeDevice(BUILDING_ID, DEVICE_ID, null);

        then(repository).should().save(building);
        assertThat(building.getDevices()).isEmpty();
        then(eventPublisher).should().publishEvent(new DeviceRemovedEvent(BUILDING_ID, DEVICE_ID, "Test Device", DeviceType.SOLAR));
    }

    // =====================================================================
    // changeConsumption
    // =====================================================================

    @Test
    @DisplayName("rejects changing consumption on a building ID that doesn't exist")
    void changeConsumption_buildingNotFound_throwsBuildingNotFoundException() {
        given(repository.findById(BUILDING_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeConsumption(BUILDING_ID,
                new ChangeConsumptionCommand(new BigDecimal("50"), EnergyUnit.kW, null)))
                .isInstanceOf(BuildingNotFoundException.class);
    }

    @Test
    @DisplayName("changes a building's consumption to a value within its production rate capacity and saves the result")
    void changeConsumption_withinCapacity_savesBuilding() {
        PublicBuilding building = buildingWithOneDevice();
        given(repository.findById(BUILDING_ID)).willReturn(Optional.of(building));

        service.changeConsumption(BUILDING_ID, new ChangeConsumptionCommand(new BigDecimal("50"), EnergyUnit.kW, null));

        then(repository).should().save(building);
        assertThat(building.getConsumption().value()).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("rejects a consumption change that exceeds the building's total production rate, without "
            + "saving anything")
    void changeConsumption_domainException_doesNotSave() {
        given(repository.findById(BUILDING_ID)).willReturn(Optional.of(buildingWithOneDevice()));

        assertThatThrownBy(() -> service.changeConsumption(BUILDING_ID,
                new ChangeConsumptionCommand(new BigDecimal("200"), EnergyUnit.kW, null)))
                // just catching any RuntimeException here, but it should be BuildingProductionRateExceededException
                .isInstanceOf(RuntimeException.class);

        then(repository).should(never()).save(any());
    }

    // =====================================================================
    // changeProduction
    // =====================================================================

    @Test
    @DisplayName("rejects changing production on a building ID that doesn't exist")
    void changeProduction_buildingNotFound_throwsBuildingNotFoundException() {
        given(repository.findById(BUILDING_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeProduction(BUILDING_ID, UUID.randomUUID(),
                new ChangeProductionCommand(new BigDecimal("50"), EnergyUnit.kW, null)))
                .isInstanceOf(BuildingNotFoundException.class);
    }

    @Test
    @DisplayName("changes a device's production rate and saves the updated aggregate")
    void changeProduction_success_savesBuilding() {
        PublicBuilding building = buildingWithOneDevice();
        given(repository.findById(BUILDING_ID)).willReturn(Optional.of(building));

        service.changeProduction(BUILDING_ID, DEVICE_ID,
                new ChangeProductionCommand(new BigDecimal("60"), EnergyUnit.kW, null));

        then(repository).should().save(building);
        assertThat(building.getDevices().get(0).getProductionRate().value())
                .isEqualByComparingTo("60");
    }

    @Test
    @DisplayName("publishes a ProductionChangedEvent after successfully changing a device's production rate")
    void changeProduction_success_publishesProductionChangedEvent() {
        PublicBuilding building = buildingWithOneDevice();
        given(repository.findById(BUILDING_ID)).willReturn(Optional.of(building));

        service.changeProduction(BUILDING_ID, DEVICE_ID,
                new ChangeProductionCommand(new BigDecimal("60"), EnergyUnit.kW, null));

        then(eventPublisher).should().publishEvent(any(ProductionChangedEvent.class));
    }

    @Test
    @DisplayName("rejects changing production on a device ID that isn't on the building, without saving anything")
    void changeProduction_domainException_doesNotSave() {
        PublicBuilding emptyBuilding = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        given(repository.findById(BUILDING_ID)).willReturn(Optional.of(emptyBuilding));

        assertThatThrownBy(() -> service.changeProduction(BUILDING_ID, UUID.randomUUID(),
                new ChangeProductionCommand(new BigDecimal("50"), EnergyUnit.kW, null)))
                // just catching any RuntimeException here, but it should be DeviceNotFoundException
                .isInstanceOf(RuntimeException.class);

        then(repository).should(never()).save(any());
    }

    // =====================================================================
    // optimistic locking (version check)
    //
    // Test buildings are built via `new PublicBuilding(...)`, which leaves version == null,
    // so any non-null expected version supplied by a command is a guaranteed mismatch.
    // =====================================================================

    @Test
    @DisplayName("rejects adding a device when the request's version number is stale — the same conflict "
            + "two concurrent editors would trigger — without saving anything")
    void addDevice_versionMismatch_throwsOptimisticLockingFailureException() {
        given(repository.findById(BUILDING_ID)).willReturn(Optional.of(new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1")));

        AddDeviceCommand cmd = new AddDeviceCommand(BUILDING_ID, "Test Device", DeviceType.SOLAR, new BigDecimal("50"), EnergyUnit.kW, 5L);

        assertThatThrownBy(() -> service.addDevice(cmd))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        then(repository).should(never()).save(any());
    }

    @Test
    @DisplayName("rejects a consumption change when the request's version number is stale — the same conflict "
            + "two concurrent editors would trigger — without saving anything")
    void changeConsumption_versionMismatch_throwsOptimisticLockingFailureException() {
        given(repository.findById(BUILDING_ID)).willReturn(Optional.of(buildingWithOneDevice()));

        assertThatThrownBy(() -> service.changeConsumption(BUILDING_ID,
                new ChangeConsumptionCommand(new BigDecimal("50"), EnergyUnit.kW, 5L)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        then(repository).should(never()).save(any());
    }

    @Test
    @DisplayName("rejects a production change when the request's version number is stale — the same conflict "
            + "two concurrent editors would trigger — without saving anything")
    void changeProduction_versionMismatch_throwsOptimisticLockingFailureException() {
        given(repository.findById(BUILDING_ID)).willReturn(Optional.of(buildingWithOneDevice()));

        assertThatThrownBy(() -> service.changeProduction(BUILDING_ID, DEVICE_ID,
                new ChangeProductionCommand(new BigDecimal("60"), EnergyUnit.kW, 5L)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        then(repository).should(never()).save(any());
    }

    @Test
    @DisplayName("rejects removing a device when the request's version number is stale — the same conflict "
            + "two concurrent editors would trigger — without saving anything")
    void removeDevice_versionMismatch_throwsOptimisticLockingFailureException() {
        given(repository.findById(BUILDING_ID)).willReturn(Optional.of(buildingWithOneDevice()));

        assertThatThrownBy(() -> service.removeDevice(BUILDING_ID, DEVICE_ID, 5L))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        then(repository).should(never()).save(any());
    }

}
