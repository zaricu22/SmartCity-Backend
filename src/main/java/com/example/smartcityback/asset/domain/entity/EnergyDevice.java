package com.example.smartcityback.asset.domain.entity;

import com.example.smartcityback.asset.domain.exception.DeviceCapacityLimitException;
import com.example.smartcityback.asset.domain.exception.ValidationException;
import com.example.smartcityback.asset.domain.shared.enums.DeviceType;
import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;
import com.example.smartcityback.asset.domain.shared.enums.ErrorCode;
import com.example.smartcityback.asset.domain.valueobject.Energy;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public class EnergyDevice {
    private UUID id;
    private DeviceType type;

    private final Energy deviceRatedCapacity;
    private Energy productionRate;

    // Private constructor for reconstitution from persistence — bypasses validation because
    // data from the DB is already valid (was validated on write). deviceRatedCapacity is final
    // so a constructor overload is required; a private no-arg constructor cannot assign final fields.
    private EnergyDevice(UUID id, DeviceType type, Energy deviceRatedCapacity, Energy productionRate) {
        this.id = id;
        this.type = type;
        this.deviceRatedCapacity = deviceRatedCapacity;
        this.productionRate = productionRate;
    }

    public static EnergyDevice reconstitute(UUID id, DeviceType type,
                                             Energy ratedCapacity, Energy productionRate) {
        return new EnergyDevice(id, type, ratedCapacity, productionRate);
    }

    public EnergyDevice(UUID id, DeviceType type, Energy deviceRatedCapacity) {
        this.id = id;
        if (type == null) {
            throw new ValidationException("Device type is required!", ErrorCode.DEVICE_TYPE_REQUIRED);
        }
        if (deviceRatedCapacity == null) {
            throw new ValidationException("Device rated capacity is required!", ErrorCode.DEVICE_CAPACITY_REQUIRED);
        }

        // required minimum
        this.type = type;
        this.deviceRatedCapacity = deviceRatedCapacity;

        this.productionRate = new Energy(new BigDecimal(0), EnergyUnit.kW);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EnergyDevice that = (EnergyDevice) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    // Domain methods
    private Energy remainingCapacity() {
        return new Energy(
                deviceRatedCapacity.value().subtract(productionRate.value()),
                deviceRatedCapacity.unit()
            );
    }

    public void changeProduction(Energy newProductionRate){
        if (newProductionRate.greaterThan(deviceRatedCapacity)){
            throw new DeviceCapacityLimitException();
        }

        this.productionRate = newProductionRate;
    }

    // Immutable getters
    public UUID getId() { return id; }
    public DeviceType getType() { return type; }
    public Energy getDeviceRatedCapacity() { return deviceRatedCapacity; }
    public Energy getProductionRate() { return productionRate; }
}
