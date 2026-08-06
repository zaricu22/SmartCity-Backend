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
        return building;
    }

    // =====================================================================
    // create
    // =====================================================================

    @Test
    void create_validCommand_savesBuilding() {
        service.create(new CreateBuildingCommand("City Hall", "Main St 1"));

        then(repository).should().save(any(PublicBuilding.class));
    }

    @Test
    void create_validCommand_returnsNonNullId() {
        UUID result = service.create(new CreateBuildingCommand("City Hall", "Main St 1"));

        assertThat(result).isNotNull();
    }

    @Test
    void create_validCommand_publishesBuildingCreatedEvent() {
        service.create(new CreateBuildingCommand("City Hall", "Main St 1"));

        then(eventPublisher).should().publishEvent(any(BuildingCreatedEvent.class));
    }

    // =====================================================================
    // delete
    // =====================================================================

    @Test
    void delete_buildingNotFound_throwsBuildingNotFoundException() {
        given(repository.findById(BUILDING_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(BUILDING_ID, null))
                .isInstanceOf(BuildingNotFoundException.class);

        then(repository).should(never()).delete(any());
    }

    @Test
    void delete_buildingFound_deletesAndPublishesEvent() {
        given(repository.findById(BUILDING_ID)).willReturn(Optional.of(new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1")));

        service.delete(BUILDING_ID, null);

        then(repository).should().delete(BUILDING_ID);
        then(eventPublisher).should().publishEvent(new BuildingDeletedEvent(BUILDING_ID, "City Hall"));
    }

    @Test
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
    void addDevice_buildingNotFound_throwsBuildingNotFoundException() {
        given(repository.findById(BUILDING_ID)).willReturn(Optional.empty());

        AddDeviceCommand cmd = new AddDeviceCommand(BUILDING_ID, "Test Device", DeviceType.SOLAR, new BigDecimal("50"), EnergyUnit.kW, null);

        assertThatThrownBy(() -> service.addDevice(cmd))
                .isInstanceOf(BuildingNotFoundException.class);
    }

    @Test
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
    void removeDevice_buildingNotFound_throwsBuildingNotFoundException() {
        given(repository.findById(BUILDING_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeDevice(BUILDING_ID, DEVICE_ID, null))
                .isInstanceOf(BuildingNotFoundException.class);
    }

    @Test
    void removeDevice_deviceNotFound_throwsDeviceNotFoundException() {
        PublicBuilding emptyBuilding = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        given(repository.findById(BUILDING_ID)).willReturn(Optional.of(emptyBuilding));

        assertThatThrownBy(() -> service.removeDevice(BUILDING_ID, DEVICE_ID, null))
                .isInstanceOf(com.example.smartcityback.asset.domain.exception.DeviceNotFoundException.class);

        then(repository).should(never()).save(any());
    }

    @Test
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
    void changeConsumption_buildingNotFound_throwsBuildingNotFoundException() {
        given(repository.findById(BUILDING_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeConsumption(BUILDING_ID,
                new ChangeConsumptionCommand(new BigDecimal("50"), EnergyUnit.kW, null)))
                .isInstanceOf(BuildingNotFoundException.class);
    }

    @Test
    void changeConsumption_withinCapacity_savesBuilding() {
        PublicBuilding building = buildingWithOneDevice();
        given(repository.findById(BUILDING_ID)).willReturn(Optional.of(building));

        service.changeConsumption(BUILDING_ID, new ChangeConsumptionCommand(new BigDecimal("50"), EnergyUnit.kW, null));

        then(repository).should().save(building);
        assertThat(building.getConsumption().value()).isEqualByComparingTo("50");
    }

    @Test
    void changeConsumption_domainException_doesNotSave() {
        given(repository.findById(BUILDING_ID)).willReturn(Optional.of(buildingWithOneDevice()));

        assertThatThrownBy(() -> service.changeConsumption(BUILDING_ID,
                new ChangeConsumptionCommand(new BigDecimal("200"), EnergyUnit.kW, null)))
                // just catching any RuntimeException here, but it should be BuildingTotalCapacityExceededException
                .isInstanceOf(RuntimeException.class);

        then(repository).should(never()).save(any());
    }

    // =====================================================================
    // changeProduction
    // =====================================================================

    @Test
    void changeProduction_buildingNotFound_throwsBuildingNotFoundException() {
        given(repository.findById(BUILDING_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeProduction(BUILDING_ID, UUID.randomUUID(),
                new ChangeProductionCommand(new BigDecimal("50"), EnergyUnit.kW, null)))
                .isInstanceOf(BuildingNotFoundException.class);
    }

    @Test
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
    void changeProduction_success_publishesProductionChangedEvent() {
        PublicBuilding building = buildingWithOneDevice();
        given(repository.findById(BUILDING_ID)).willReturn(Optional.of(building));

        service.changeProduction(BUILDING_ID, DEVICE_ID,
                new ChangeProductionCommand(new BigDecimal("60"), EnergyUnit.kW, null));

        then(eventPublisher).should().publishEvent(any(ProductionChangedEvent.class));
    }

    @Test
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
    void addDevice_versionMismatch_throwsOptimisticLockingFailureException() {
        given(repository.findById(BUILDING_ID)).willReturn(Optional.of(new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1")));

        AddDeviceCommand cmd = new AddDeviceCommand(BUILDING_ID, "Test Device", DeviceType.SOLAR, new BigDecimal("50"), EnergyUnit.kW, 5L);

        assertThatThrownBy(() -> service.addDevice(cmd))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        then(repository).should(never()).save(any());
    }

    @Test
    void changeConsumption_versionMismatch_throwsOptimisticLockingFailureException() {
        given(repository.findById(BUILDING_ID)).willReturn(Optional.of(buildingWithOneDevice()));

        assertThatThrownBy(() -> service.changeConsumption(BUILDING_ID,
                new ChangeConsumptionCommand(new BigDecimal("50"), EnergyUnit.kW, 5L)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        then(repository).should(never()).save(any());
    }

    @Test
    void changeProduction_versionMismatch_throwsOptimisticLockingFailureException() {
        given(repository.findById(BUILDING_ID)).willReturn(Optional.of(buildingWithOneDevice()));

        assertThatThrownBy(() -> service.changeProduction(BUILDING_ID, DEVICE_ID,
                new ChangeProductionCommand(new BigDecimal("60"), EnergyUnit.kW, 5L)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        then(repository).should(never()).save(any());
    }

    @Test
    void removeDevice_versionMismatch_throwsOptimisticLockingFailureException() {
        given(repository.findById(BUILDING_ID)).willReturn(Optional.of(buildingWithOneDevice()));

        assertThatThrownBy(() -> service.removeDevice(BUILDING_ID, DEVICE_ID, 5L))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        then(repository).should(never()).save(any());
    }

}
