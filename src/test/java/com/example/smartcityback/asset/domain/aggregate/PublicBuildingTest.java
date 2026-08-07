package com.example.smartcityback.asset.domain.aggregate;

import com.example.smartcityback.asset.domain.entity.EnergyDevice;
import com.example.smartcityback.asset.domain.event.BuildingCreatedEvent;
import com.example.smartcityback.asset.domain.event.ConsumptionChangedEvent;
import com.example.smartcityback.asset.domain.event.DeviceAddedEvent;
import com.example.smartcityback.asset.domain.event.DeviceRemovedEvent;
import com.example.smartcityback.asset.domain.event.DomainEvent;
import com.example.smartcityback.asset.domain.event.ProductionChangedEvent;
import com.example.smartcityback.asset.domain.exception.BuildingTotalCapacityExceededException;
import com.example.smartcityback.asset.domain.exception.DeviceAlreadyExistsException;
import com.example.smartcityback.asset.domain.exception.DeviceNotFoundException;
import com.example.smartcityback.asset.domain.exception.ValidationException;
import com.example.smartcityback.asset.domain.shared.enums.DeviceType;
import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;
import com.example.smartcityback.asset.domain.valueobject.Energy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class PublicBuildingTest {

    private static final UUID   BUILDING_ID     = UUID.randomUUID();
    private static final UUID   DEVICE_ID       = UUID.randomUUID();
    private static final Energy CAPACITY_100_KW = new Energy(new BigDecimal("100"), EnergyUnit.kW);

    // =====================================================================
    // Construction / Validation
    // =====================================================================

    @Test
    void create_validNameAndLocation_succeeds() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");

        assertThat(building.getId()).isEqualTo(BUILDING_ID);
        assertThat(building.getName()).isEqualTo("City Hall");
        assertThat(building.getLocation()).isEqualTo("Main St 1");
        assertThat(building.getDevices()).isEmpty();
        assertThat(building.getConsumption().value()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(building.getConsumption().unit()).isEqualTo(EnergyUnit.kW);
    }

    @Test
    void create_nullName_throwsValidationException() {
        assertThatThrownBy(() -> new PublicBuilding(BUILDING_ID, null, "Main St 1"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void create_emptyName_throwsValidationException() {
        assertThatThrownBy(() -> new PublicBuilding(BUILDING_ID, "", "Main St 1"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void create_nullLocation_throwsValidationException() {
        assertThatThrownBy(() -> new PublicBuilding(BUILDING_ID, "City Hall", null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void create_emptyLocation_throwsValidationException() {
        assertThatThrownBy(() -> new PublicBuilding(BUILDING_ID, "City Hall", ""))
                .isInstanceOf(ValidationException.class);
    }

    // =====================================================================
    // addDevice
    // =====================================================================

    @Test
    void addDevice_firstDevice_isAddedSuccessfully() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        EnergyDevice device = new EnergyDevice(DEVICE_ID, "Test Device", DeviceType.SOLAR, CAPACITY_100_KW);

        building.addDevice(device);

        assertThat(building.getDevices()).hasSize(1);
        assertThat(building.getDevices().get(0).getType()).isEqualTo(DeviceType.SOLAR);
    }

    @Test
    void addDevice_twoDevicesWithDifferentIds_bothAdded() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        EnergyDevice solar   = new EnergyDevice(UUID.randomUUID(), "Test Device", DeviceType.SOLAR, CAPACITY_100_KW);
        EnergyDevice battery = new EnergyDevice(UUID.randomUUID(), "Test Device", DeviceType.BATTERY, CAPACITY_100_KW);

        building.addDevice(solar);
        building.addDevice(battery);

        assertThat(building.getDevices()).hasSize(2);
    }

    @Test
    void addDevice_duplicateDevice_throwsDeviceAlreadyExistsException() {
        // Duplicate is matched on name+type, not ID — same UUID here is incidental.
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        EnergyDevice first  = new EnergyDevice(DEVICE_ID, "Test Device", DeviceType.SOLAR, CAPACITY_100_KW);
        EnergyDevice second = new EnergyDevice(DEVICE_ID, "Test Device", DeviceType.SOLAR, CAPACITY_100_KW);

        building.addDevice(first);

        assertThatThrownBy(() -> building.addDevice(second))
                .isInstanceOf(DeviceAlreadyExistsException.class);
    }

    @Test
    void addDevice_sameNameAndTypeDifferentIds_throwsDeviceAlreadyExistsException() {
        // Regression test: AppService.addDevice() generates a fresh random UUID on every call,
        // so a retried "add device" request always produces two different IDs. Matching on
        // name+type instead of EnergyDevice.equals() (ID-based) is what makes the duplicate
        // check actually catch that retry instead of silently creating a second device.
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        building.addDevice(new EnergyDevice(UUID.randomUUID(), "Test Device", DeviceType.SOLAR, CAPACITY_100_KW));

        assertThatThrownBy(() -> building.addDevice(
                new EnergyDevice(UUID.randomUUID(), "Test Device", DeviceType.SOLAR, CAPACITY_100_KW)))
                .isInstanceOf(DeviceAlreadyExistsException.class);
    }

    @Test
    void addDevice_sameNameDifferentType_succeeds() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        building.addDevice(new EnergyDevice(UUID.randomUUID(), "Test Device", DeviceType.SOLAR, CAPACITY_100_KW));

        building.addDevice(new EnergyDevice(UUID.randomUUID(), "Test Device", DeviceType.BATTERY, CAPACITY_100_KW));

        assertThat(building.getDevices()).hasSize(2);
    }

    @Test
    void addDevice_sameTypeDifferentName_succeeds() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        building.addDevice(new EnergyDevice(UUID.randomUUID(), "Solar Panel A", DeviceType.SOLAR, CAPACITY_100_KW));

        building.addDevice(new EnergyDevice(UUID.randomUUID(), "Solar Panel B", DeviceType.SOLAR, CAPACITY_100_KW));

        assertThat(building.getDevices()).hasSize(2);
    }

    // public List<EnergyDevice> getDevices() { return Collections.unmodifiableList(devices); }
    @Test
    void addDevice_devicesListIsUnmodifiable() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        building.addDevice(new EnergyDevice(DEVICE_ID, "Test Device", DeviceType.SOLAR, CAPACITY_100_KW));

        assertThatThrownBy(() -> building.getDevices().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // =====================================================================
    // removeDevice
    // =====================================================================

    @Test
    void removeDevice_existingDevice_isRemoved() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        building.addDevice(new EnergyDevice(DEVICE_ID, "Test Device", DeviceType.SOLAR, CAPACITY_100_KW));

        building.removeDevice(DEVICE_ID);

        assertThat(building.getDevices()).isEmpty();
    }

    @Test
    void removeDevice_nonExistentDevice_throwsDeviceNotFoundException() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");

        assertThatThrownBy(() -> building.removeDevice(DEVICE_ID))
                .isInstanceOf(DeviceNotFoundException.class);
    }

    @Test
    void removeDevice_firesDeviceRemovedEvent() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        building.addDevice(new EnergyDevice(DEVICE_ID, "Test Device", DeviceType.SOLAR, CAPACITY_100_KW));
        building.pullEvents(); // clear BuildingCreatedEvent, DeviceAddedEvent

        building.removeDevice(DEVICE_ID);

        List<DomainEvent> events = building.pullEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(DeviceRemovedEvent.class);
        DeviceRemovedEvent event = (DeviceRemovedEvent) events.get(0);
        assertThat(event.buildingId()).isEqualTo(BUILDING_ID);
        assertThat(event.deviceId()).isEqualTo(DEVICE_ID);
        assertThat(event.deviceName()).isEqualTo("Test Device");
        assertThat(event.deviceType()).isEqualTo(DeviceType.SOLAR);
    }

    //* =====================================================================
    // changeConsumption
    //
    // if (newConsumptionRate.greaterThan(calculateTotalCapacity()))
    // =====================================================================

    @Test
    void changeConsumption_zeroOnEmptyBuilding_succeeds() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");

        building.changeConsumption(new Energy(BigDecimal.ZERO, EnergyUnit.kW));

        assertThat(building.getConsumption().value()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void changeConsumption_withinTotalCapacity_updatesConsumption() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        building.addDevice(new EnergyDevice(DEVICE_ID, "Test Device", DeviceType.SOLAR, CAPACITY_100_KW));

        building.changeConsumption(new Energy(new BigDecimal("50"), EnergyUnit.kW));

        assertThat(building.getConsumption().value()).isEqualByComparingTo("50");
    }

    @Test
    void changeConsumption_equalToTotalCapacity_succeeds() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        building.addDevice(new EnergyDevice(DEVICE_ID, "Test Device", DeviceType.SOLAR, CAPACITY_100_KW));

        building.changeConsumption(CAPACITY_100_KW);

        assertThat(building.getConsumption().value()).isEqualByComparingTo("100");
    }

    @Test
    void changeConsumption_exceedsTotalCapacity_throwsBuildingTotalCapacityExceededException() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        // Empty building has 0 kW total capacity — any positive consumption exceeds it.
        assertThatThrownBy(() -> building.changeConsumption(new Energy(new BigDecimal("1"), EnergyUnit.kW)))
                .isInstanceOf(BuildingTotalCapacityExceededException.class);
    }

    @Test
    void changeConsumption_exceedsTwoDevicesCapacity_throwsBuildingTotalCapacityExceededException() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        building.addDevice(new EnergyDevice(UUID.randomUUID(), "Test Device", DeviceType.SOLAR, CAPACITY_100_KW));
        building.addDevice(new EnergyDevice(UUID.randomUUID(), "Test Device", DeviceType.BATTERY, CAPACITY_100_KW));
        // Total capacity = 200 kW
        assertThatThrownBy(() -> building.changeConsumption(new Energy(new BigDecimal("201"), EnergyUnit.kW)))
                .isInstanceOf(BuildingTotalCapacityExceededException.class);
    }

    @Test
    void changeConsumption_crossUnit_0pt05MWWithin100kWCapacity_succeeds() {
        // 100 kW = 0.1 MW; 0.05 MW = 50 kW < 100 kW — must NOT throw.
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        building.addDevice(new EnergyDevice(DEVICE_ID, "Test Device", DeviceType.SOLAR, CAPACITY_100_KW));
        Energy consumptionInMW = new Energy(new BigDecimal("0.05"), EnergyUnit.MW);

        assertThatCode(() -> building.changeConsumption(consumptionInMW)).doesNotThrowAnyException();
    }

    @Test
    void changeConsumption_crossUnit_0pt2MWExceeds100kWCapacity_throws() {
        // 0.2 MW = 200 kW > 100 kW total capacity — must throw.
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        building.addDevice(new EnergyDevice(DEVICE_ID, "Test Device", DeviceType.SOLAR, CAPACITY_100_KW));
        Energy consumptionInMW = new Energy(new BigDecimal("0.2"), EnergyUnit.MW);

        assertThatThrownBy(() -> building.changeConsumption(consumptionInMW))
                .isInstanceOf(BuildingTotalCapacityExceededException.class);
    }

    // =====================================================================
    // changeDeviceProduction
    //
    // .orElseThrow(DeviceNotFoundException::new);
    // =====================================================================

    @Test
    void changeDeviceProduction_deviceNotFound_throwsDeviceNotFoundException() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        // Device not exists

        assertThatThrownBy(() -> building.changeDeviceProduction(UUID.randomUUID(), CAPACITY_100_KW))
                .isInstanceOf(DeviceNotFoundException.class);
    }

    @Test
    void changeDeviceProduction_unknownIdOnNonEmptyBuilding_throwsDeviceNotFoundException() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        building.addDevice(new EnergyDevice(DEVICE_ID, "Test Device", DeviceType.SOLAR, CAPACITY_100_KW));

        assertThatThrownBy(() -> building.changeDeviceProduction(UUID.randomUUID(), CAPACITY_100_KW))
                .isInstanceOf(DeviceNotFoundException.class);
    }

    @Test
    void changeDeviceProduction_existingDevice_updatesProductionRate() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        EnergyDevice device = new EnergyDevice(DEVICE_ID, "Test Device", DeviceType.SOLAR, CAPACITY_100_KW);
        building.addDevice(device);
        Energy production = new Energy(new BigDecimal("60"), EnergyUnit.kW);

        building.changeDeviceProduction(DEVICE_ID, production);

        assertThat(building.getDevices().get(0).getProductionRate().value())
                .isEqualByComparingTo("60");
    }

    // =====================================================================
    // Domain events
    // =====================================================================

    @Test
    void construction_firesBuildingCreatedEvent() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");

        List<DomainEvent> events = building.pullEvents();

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(BuildingCreatedEvent.class);
        BuildingCreatedEvent event = (BuildingCreatedEvent) events.get(0);
        assertThat(event.buildingId()).isEqualTo(BUILDING_ID);
        assertThat(event.name()).isEqualTo("City Hall");
        assertThat(event.location()).isEqualTo("Main St 1");
    }

    @Test
    void addDevice_firesDeviceAddedEvent() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        building.pullEvents(); // clear BuildingCreatedEvent
        building.addDevice(new EnergyDevice(DEVICE_ID, "Test Device", DeviceType.SOLAR, CAPACITY_100_KW));

        List<DomainEvent> events = building.pullEvents();

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(DeviceAddedEvent.class);
        DeviceAddedEvent event = (DeviceAddedEvent) events.get(0);
        assertThat(event.buildingId()).isEqualTo(BUILDING_ID);
        assertThat(event.deviceId()).isEqualTo(DEVICE_ID);
        assertThat(event.deviceName()).isEqualTo("Test Device");
        assertThat(event.deviceType()).isEqualTo(DeviceType.SOLAR);
    }

    @Test
    void changeConsumption_firesConsumptionChangedEvent() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        building.addDevice(new EnergyDevice(DEVICE_ID, "Test Device", DeviceType.SOLAR, CAPACITY_100_KW));
        building.pullEvents(); // clear DeviceAddedEvent

        Energy newConsumption = new Energy(new BigDecimal("50"), EnergyUnit.kW);
        building.changeConsumption(newConsumption);

        List<DomainEvent> events = building.pullEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(ConsumptionChangedEvent.class);
        ConsumptionChangedEvent event = (ConsumptionChangedEvent) events.get(0);
        assertThat(event.buildingId()).isEqualTo(BUILDING_ID);
        assertThat(event.oldConsumption().value()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(event.newConsumption().value()).isEqualByComparingTo("50");
    }

    @Test
    void changeDeviceProduction_existingDevice_firesProductionChangedEvent() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        building.addDevice(new EnergyDevice(DEVICE_ID, "Test Device", DeviceType.SOLAR, CAPACITY_100_KW));
        building.pullEvents(); // clear DeviceAddedEvent

        Energy newProduction = new Energy(new BigDecimal("60"), EnergyUnit.kW);
        building.changeDeviceProduction(DEVICE_ID, newProduction);

        List<DomainEvent> events = building.pullEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(ProductionChangedEvent.class);
        ProductionChangedEvent event = (ProductionChangedEvent) events.get(0);
        assertThat(event.buildingId()).isEqualTo(BUILDING_ID);
        assertThat(event.deviceId()).isEqualTo(DEVICE_ID);
        assertThat(event.oldProduction().value()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(event.newProduction().value()).isEqualByComparingTo("60");
    }

    @Test
    void pullEvents_clearsEventList() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        building.addDevice(new EnergyDevice(DEVICE_ID, "Test Device", DeviceType.SOLAR, CAPACITY_100_KW));

        building.pullEvents();

        assertThat(building.pullEvents()).isEmpty();
    }

    // =====================================================================
    // Equality / hashCode - because on ID so no need to test them separately
    // =====================================================================
}
