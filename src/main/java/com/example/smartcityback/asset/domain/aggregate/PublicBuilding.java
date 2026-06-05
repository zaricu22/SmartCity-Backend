package com.example.smartcityback.asset.domain.aggregate;

import com.example.smartcityback.asset.domain.entity.EnergyDevice;
import com.example.smartcityback.asset.domain.event.ConsumptionChangedEvent;
import com.example.smartcityback.asset.domain.event.DeviceAddedEvent;
import com.example.smartcityback.asset.domain.event.DomainEvent;
import com.example.smartcityback.asset.domain.exception.BuildingTotalCapacityExceededException;
import com.example.smartcityback.asset.domain.exception.DeviceAlreadyExistsException;
import com.example.smartcityback.asset.domain.exception.DeviceNotFoundException;
import com.example.smartcityback.asset.domain.exception.ValidationException;
import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;
import com.example.smartcityback.asset.domain.shared.enums.ErrorCode;
import com.example.smartcityback.asset.domain.valueobject.*;

import java.math.BigDecimal;
import java.util.*;

public class PublicBuilding {
    private UUID id;
    private String name;
    private String location;

    private Energy consumption;
    private List<EnergyDevice> devices;

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    // Private no-arg constructor for reconstitution from persistence — bypasses validation
    // because data from the DB is already valid (was validated on write). Fields are not final
    // so the static factory can assign them directly after instantiation. domainEvents is
    // final but initialized inline so it is available even with this no-arg constructor.
    private PublicBuilding() {}

    public static PublicBuilding reconstitute(UUID id, String name, String location,
                                               List<EnergyDevice> devices, Energy consumption) {
        PublicBuilding b = new PublicBuilding();
        b.id = id;
        b.name = name;
        b.location = location;
        b.devices = new ArrayList<>(devices);
        b.consumption = consumption;
        return b;
    }

    public PublicBuilding(UUID id, String name, String location) {
        if (name == null || name.isEmpty())
            throw new ValidationException("Building must have a name!", ErrorCode.BUILDING_NAME_EMPTY);
        if (location == null || location.isEmpty())
            throw new ValidationException("Building must have an address!", ErrorCode.BUILDING_ADDRESS_EMPTY);

        // required minimum
        this.id = id;
        this.name = name;
        this.location = location;

        this.devices = new ArrayList<>();
        this.consumption = new Energy(new BigDecimal(0), EnergyUnit.kW);
    }

    public List<DomainEvent> pullEvents() {
        List<DomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PublicBuilding that = (PublicBuilding) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    // Domain methods
    public void addDevice(EnergyDevice newDevice) {
        if (devices.stream().anyMatch(d -> d.equals(newDevice))) {
            throw new DeviceAlreadyExistsException();
        }

        this.devices.add(newDevice);

        domainEvents.add(new DeviceAddedEvent(id, newDevice.getId(), newDevice.getType()));
    }

    private Energy calculateTotalCapacity() {
        BigDecimal total = devices.stream()
                .map(EnergyDevice::getDeviceRatedCapacity)
                .map(e -> e.to(EnergyUnit.kW).value())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new Energy(total, EnergyUnit.kW);  // kW - default Domain unit
    }

    public void changeConsumption(Energy newConsumptionRate){
        if (newConsumptionRate.greaterThan(calculateTotalCapacity())) {
            throw new BuildingTotalCapacityExceededException();
        }

        Energy old = this.consumption;
        this.consumption = newConsumptionRate;

        domainEvents.add(new ConsumptionChangedEvent(id, old, newConsumptionRate));
    }

    public void changeDeviceProduction(UUID deviceId, Energy production) {
        EnergyDevice device = devices.stream()
                .filter(d -> d.getId().equals(deviceId))
                .findFirst()
                .orElseThrow(DeviceNotFoundException::new);

        device.changeProduction(production);
    }

    // Immutable getters
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getLocation() { return location; }
    public Energy getConsumption() { return consumption; }
    public List<EnergyDevice> getDevices() { return Collections.unmodifiableList(devices); }
}
