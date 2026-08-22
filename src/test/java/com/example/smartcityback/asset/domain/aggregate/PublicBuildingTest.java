package com.example.smartcityback.asset.domain.aggregate;

import com.example.smartcityback.asset.domain.entity.EnergyDevice;
import com.example.smartcityback.asset.domain.event.BuildingCreatedEvent;
import com.example.smartcityback.asset.domain.event.ConsumptionChangedEvent;
import com.example.smartcityback.asset.domain.event.DeviceAddedEvent;
import com.example.smartcityback.asset.domain.event.DeviceRemovedEvent;
import com.example.smartcityback.asset.domain.event.DomainEvent;
import com.example.smartcityback.asset.domain.event.ProductionChangedEvent;
import com.example.smartcityback.asset.domain.exception.BuildingProductionRateExceededException;
import com.example.smartcityback.asset.domain.exception.DeviceAlreadyExistsException;
import com.example.smartcityback.asset.domain.exception.DeviceInUseException;
import com.example.smartcityback.asset.domain.exception.DeviceNotFoundException;
import com.example.smartcityback.asset.domain.exception.ValidationException;
import com.example.smartcityback.asset.domain.shared.enums.DeviceType;
import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;
import com.example.smartcityback.asset.domain.valueobject.Energy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class PublicBuildingTest {

    private static final UUID   BUILDING_ID     = UUID.randomUUID();
    private static final UUID   DEVICE_ID       = UUID.randomUUID();
    private static final Energy CAPACITY_100_KW = new Energy(new BigDecimal("100"), EnergyUnit.kW);

    // Adds a device to the building and immediately sets its production rate to match its
    // capacity — the "fully producing" fixture shape changeConsumption() tests need, since the
    // invariant checks production rate rather than rated capacity. Sets production directly on
    // the entity (not via PublicBuilding.changeDeviceProduction()), so it publishes no event.
    private static EnergyDevice addProducingDevice(PublicBuilding building, UUID deviceId, DeviceType type, Energy capacity) {
        EnergyDevice device = new EnergyDevice(deviceId, "Test Device", type, capacity);
        building.addDevice(device);
        device.changeProduction(capacity);
        return device;
    }

    private static EnergyDevice addProducingDevice(PublicBuilding building) {
        return addProducingDevice(building, DEVICE_ID, DeviceType.SOLAR, CAPACITY_100_KW);
    }

    // =====================================================================
    // Construction / Validation
    // =====================================================================

    @Test
    @DisplayName("creates a building with its ID, name, and location set, no devices, and consumption "
            + "starting at zero")
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
    @DisplayName("rejects creating a building with no name")
    void create_nullName_throwsValidationException() {
        assertThatThrownBy(() -> new PublicBuilding(BUILDING_ID, null, "Main St 1"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("rejects creating a building whose name is an empty string")
    void create_emptyName_throwsValidationException() {
        assertThatThrownBy(() -> new PublicBuilding(BUILDING_ID, "", "Main St 1"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("rejects creating a building with no location")
    void create_nullLocation_throwsValidationException() {
        assertThatThrownBy(() -> new PublicBuilding(BUILDING_ID, "City Hall", null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("rejects creating a building whose location is an empty string")
    void create_emptyLocation_throwsValidationException() {
        assertThatThrownBy(() -> new PublicBuilding(BUILDING_ID, "City Hall", ""))
                .isInstanceOf(ValidationException.class);
    }

    // =====================================================================
    // addDevice
    // =====================================================================

    @Test
    @DisplayName("adds a device to a building that has no existing devices")
    void addDevice_firstDevice_isAddedSuccessfully() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        EnergyDevice device = new EnergyDevice(DEVICE_ID, "Test Device", DeviceType.SOLAR, CAPACITY_100_KW);

        building.addDevice(device);

        assertThat(building.getDevices()).hasSize(1);
        assertThat(building.getDevices().get(0).getType()).isEqualTo(DeviceType.SOLAR);
    }

    @Test
    @DisplayName("adds two devices of different types to the same building, keeping both")
    void addDevice_twoDevicesWithDifferentIds_bothAdded() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        EnergyDevice solar   = new EnergyDevice(UUID.randomUUID(), "Test Device", DeviceType.SOLAR, CAPACITY_100_KW);
        EnergyDevice battery = new EnergyDevice(UUID.randomUUID(), "Test Device", DeviceType.BATTERY, CAPACITY_100_KW);

        building.addDevice(solar);
        building.addDevice(battery);

        assertThat(building.getDevices()).hasSize(2);
    }

    @Test
    @DisplayName("rejects adding a second device with the same name, type, and ID as one already on the building")
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
    @DisplayName("rejects a retried add-device request even when it arrives with a different device ID — the "
            + "duplicate check matches on name+type, not ID, which is what lets it catch a retry instead of "
            + "silently creating a second device")
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
    @DisplayName("allows two devices with the same name but different types on the same building")
    void addDevice_sameNameDifferentType_succeeds() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        building.addDevice(new EnergyDevice(UUID.randomUUID(), "Test Device", DeviceType.SOLAR, CAPACITY_100_KW));

        building.addDevice(new EnergyDevice(UUID.randomUUID(), "Test Device", DeviceType.BATTERY, CAPACITY_100_KW));

        assertThat(building.getDevices()).hasSize(2);
    }

    @Test
    @DisplayName("allows two devices of the same type but different names on the same building")
    void addDevice_sameTypeDifferentName_succeeds() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        building.addDevice(new EnergyDevice(UUID.randomUUID(), "Solar Panel A", DeviceType.SOLAR, CAPACITY_100_KW));

        building.addDevice(new EnergyDevice(UUID.randomUUID(), "Solar Panel B", DeviceType.SOLAR, CAPACITY_100_KW));

        assertThat(building.getDevices()).hasSize(2);
    }

    // public List<EnergyDevice> getDevices() { return Collections.unmodifiableList(devices); }
    @Test
    @DisplayName("returns a device list that can't be mutated from outside the aggregate — clearing it "
            + "throws instead of silently succeeding")
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
    @DisplayName("removes an existing device from the building")
    void removeDevice_existingDevice_isRemoved() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        building.addDevice(new EnergyDevice(DEVICE_ID, "Test Device", DeviceType.SOLAR, CAPACITY_100_KW));

        building.removeDevice(DEVICE_ID);

        assertThat(building.getDevices()).isEmpty();
    }

    @Test
    @DisplayName("rejects removing a device ID that isn't on the building")
    void removeDevice_nonExistentDevice_throwsDeviceNotFoundException() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");

        assertThatThrownBy(() -> building.removeDevice(DEVICE_ID))
                .isInstanceOf(DeviceNotFoundException.class);
    }

    @Test
    @DisplayName("publishes exactly one DeviceRemovedEvent, carrying the removed device's ID, name, and "
            + "type, when a device is removed")
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

    @Test
    @DisplayName("rejects removing a device whose production the building's current consumption still "
            + "depends on, leaving it on the building")
    void removeDevice_remainingProductionInsufficientForConsumption_throwsDeviceInUseException() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        addProducingDevice(building);
        // Sole device produces 100 kW — consumption at 50 kW means removing it would leave
        // 0 kW of production against 50 kW of consumption.
        building.changeConsumption(new Energy(new BigDecimal("50"), EnergyUnit.kW));

        assertThatThrownBy(() -> building.removeDevice(DEVICE_ID))
                .isInstanceOf(DeviceInUseException.class);
        assertThat(building.getDevices()).hasSize(1);
    }

    //* =====================================================================
    // changeConsumption
    //
    // if (newConsumptionRate.greaterThan(calculateTotalProductionRate()))
    // =====================================================================

    @Test
    @DisplayName("allows setting consumption to zero on a building with no devices")
    void changeConsumption_zeroOnEmptyBuilding_succeeds() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");

        building.changeConsumption(new Energy(BigDecimal.ZERO, EnergyUnit.kW));

        assertThat(building.getConsumption().value()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("changes consumption to a value within the building's total production rate")
    void changeConsumption_withinProductionRate_updatesConsumption() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        addProducingDevice(building);

        building.changeConsumption(new Energy(new BigDecimal("50"), EnergyUnit.kW));

        assertThat(building.getConsumption().value()).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("allows setting consumption exactly at the building's total production rate, not just "
            + "strictly below it")
    void changeConsumption_equalToProductionRate_succeeds() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        addProducingDevice(building);

        building.changeConsumption(CAPACITY_100_KW);

        assertThat(building.getConsumption().value()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("rejects any positive consumption on a building with no devices, since its total production rate is 0 kW")
    void changeConsumption_exceedsProductionRate_throwsBuildingProductionRateExceededException() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        // Empty building has 0 kW production — any positive consumption exceeds it.
        assertThatThrownBy(() -> building.changeConsumption(new Energy(new BigDecimal("1"), EnergyUnit.kW)))
                .isInstanceOf(BuildingProductionRateExceededException.class);
    }

    @Test
    @DisplayName("rejects a consumption request of 201 kW against a building with two 100 kW devices, whose "
            + "production rates sum to 200 kW")
    void changeConsumption_exceedsTwoDevicesProductionRate_throwsBuildingProductionRateExceededException() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        addProducingDevice(building, UUID.randomUUID(), DeviceType.SOLAR, CAPACITY_100_KW);
        addProducingDevice(building, UUID.randomUUID(), DeviceType.BATTERY, CAPACITY_100_KW);
        // ProductionRate = 200 kW
        assertThatThrownBy(() -> building.changeConsumption(new Energy(new BigDecimal("201"), EnergyUnit.kW)))
                .isInstanceOf(BuildingProductionRateExceededException.class);
    }

    @Test
    @DisplayName("allows a consumption request of 0.05 MW (50 kW) against a device rated in kW — proves the "
            + "capacity check converts units instead of comparing the raw numbers 0.05 and 100")
    void changeConsumption_crossUnit_0pt05MWWithin100kWCapacity_succeeds() {
        // 100 kW = 0.1 MW; 0.05 MW = 50 kW < 100 kW — must NOT throw.
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        addProducingDevice(building);
        Energy consumptionInMW = new Energy(new BigDecimal("0.05"), EnergyUnit.MW);

        assertThatCode(() -> building.changeConsumption(consumptionInMW)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejects a consumption request of 0.2 MW (200 kW) against a building whose devices only total 100 kW")
    void changeConsumption_crossUnit_0pt2MWExceeds100kWCapacity_throws() {
        // 0.2 MW = 200 kW > 100 kW production rate — must throw.
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        addProducingDevice(building);
        Energy consumptionInMW = new Energy(new BigDecimal("0.2"), EnergyUnit.MW);

        assertThatThrownBy(() -> building.changeConsumption(consumptionInMW))
                .isInstanceOf(BuildingProductionRateExceededException.class);
    }

    // =====================================================================
    // changeDeviceProduction
    //
    // .orElseThrow(DeviceNotFoundException::new);
    // =====================================================================

    @Test
    @DisplayName("rejects changing production for a device ID on a building that has no devices at all")
    void changeDeviceProduction_deviceNotFound_throwsDeviceNotFoundException() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        // Device not exists

        assertThatThrownBy(() -> building.changeDeviceProduction(UUID.randomUUID(), CAPACITY_100_KW))
                .isInstanceOf(DeviceNotFoundException.class);
    }

    @Test
    @DisplayName("rejects changing production for a device ID that isn't on the building, even though the "
            + "building does have a different device")
    void changeDeviceProduction_unknownIdOnNonEmptyBuilding_throwsDeviceNotFoundException() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        building.addDevice(new EnergyDevice(DEVICE_ID, "Test Device", DeviceType.SOLAR, CAPACITY_100_KW));

        assertThatThrownBy(() -> building.changeDeviceProduction(UUID.randomUUID(), CAPACITY_100_KW))
                .isInstanceOf(DeviceNotFoundException.class);
    }

    @Test
    @DisplayName("changes an existing device's production rate")
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
    @DisplayName("publishes exactly one BuildingCreatedEvent, carrying the building's ID, name, and "
            + "location, when a building is constructed")
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
    @DisplayName("publishes exactly one DeviceAddedEvent, carrying the new device's ID, name, and type, "
            + "when a device is added")
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
    @DisplayName("publishes exactly one ConsumptionChangedEvent carrying both the old (0 kW) and new (50 kW) "
            + "consumption values")
    void changeConsumption_firesConsumptionChangedEvent() {
        PublicBuilding building = new PublicBuilding(BUILDING_ID, "City Hall", "Main St 1");
        addProducingDevice(building);
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
    @DisplayName("publishes exactly one ProductionChangedEvent carrying both the old (0 kW) and new (60 kW) "
            + "production values")
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
    @DisplayName("returns an empty list on a second pullEvents() call, proving the first call actually "
            + "cleared the pending events instead of just reading them")
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
