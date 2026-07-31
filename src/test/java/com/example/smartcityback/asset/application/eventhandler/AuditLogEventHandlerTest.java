package com.example.smartcityback.asset.application.eventhandler;

import com.example.smartcityback.asset.domain.event.BuildingCreatedEvent;
import com.example.smartcityback.asset.domain.event.BuildingDeletedEvent;
import com.example.smartcityback.asset.domain.event.ConsumptionChangedEvent;
import com.example.smartcityback.asset.domain.event.DeviceAddedEvent;
import com.example.smartcityback.asset.domain.event.DeviceRemovedEvent;
import com.example.smartcityback.asset.domain.event.ProductionChangedEvent;
import com.example.smartcityback.asset.domain.shared.enums.DeviceType;
import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;
import com.example.smartcityback.asset.domain.valueobject.Energy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for AuditLogEventHandler.
 *
 * The handler only logs — there is no return value or state to assert.
 * Tests verify that the handler does not throw for any valid event,
 * confirming it handles all event types without error.
 */
@ExtendWith(MockitoExtension.class)
class AuditLogEventHandlerTest {

    private final AuditLogEventHandler handler = new AuditLogEventHandler();

    private static final UUID BUILDING_ID = UUID.randomUUID();
    private static final UUID DEVICE_ID   = UUID.randomUUID();

    // =====================================================================
    // BuildingCreatedEvent
    // =====================================================================

    @Test
    void onBuildingCreated_validEvent_doesNotThrow() {
        BuildingCreatedEvent event = new BuildingCreatedEvent(BUILDING_ID, "City Hall", "Main St 1");

        assertThatCode(() -> handler.onBuildingCreated(event))
                .doesNotThrowAnyException();
    }

    // =====================================================================
    // BuildingDeletedEvent
    // =====================================================================

    @Test
    void onBuildingDeleted_validEvent_doesNotThrow() {
        BuildingDeletedEvent event = new BuildingDeletedEvent(BUILDING_ID);

        assertThatCode(() -> handler.onBuildingDeleted(event))
                .doesNotThrowAnyException();
    }

    // =====================================================================
    // DeviceAddedEvent
    // =====================================================================

    @Test
    void onDeviceAdded_validEvent_doesNotThrow() {
        DeviceAddedEvent event = new DeviceAddedEvent(BUILDING_ID, DEVICE_ID, DeviceType.SOLAR);

        assertThatCode(() -> handler.onDeviceAdded(event))
                .doesNotThrowAnyException();
    }

    @Test
    void onDeviceAdded_allDeviceTypes_doesNotThrow() {
        for (DeviceType type : DeviceType.values()) {
            DeviceAddedEvent event = new DeviceAddedEvent(BUILDING_ID, DEVICE_ID, type);

            assertThatCode(() -> handler.onDeviceAdded(event))
                    .doesNotThrowAnyException();
        }
    }

    // =====================================================================
    // DeviceRemovedEvent
    // =====================================================================

    @Test
    void onDeviceRemoved_validEvent_doesNotThrow() {
        DeviceRemovedEvent event = new DeviceRemovedEvent(BUILDING_ID, DEVICE_ID);

        assertThatCode(() -> handler.onDeviceRemoved(event))
                .doesNotThrowAnyException();
    }

    // =====================================================================
    // ConsumptionChangedEvent
    // =====================================================================

    @Test
    void onConsumptionChanged_validEvent_doesNotThrow() {
        ConsumptionChangedEvent event = new ConsumptionChangedEvent(
                BUILDING_ID,
                new Energy(BigDecimal.ZERO, EnergyUnit.kW),
                new Energy(new BigDecimal("50"), EnergyUnit.kW)
        );

        assertThatCode(() -> handler.onConsumptionChanged(event))
                .doesNotThrowAnyException();
    }

    @Test
    void onConsumptionChanged_zeroToZero_doesNotThrow() {
        ConsumptionChangedEvent event = new ConsumptionChangedEvent(
                BUILDING_ID,
                new Energy(BigDecimal.ZERO, EnergyUnit.kW),
                new Energy(BigDecimal.ZERO, EnergyUnit.kW)
        );

        assertThatCode(() -> handler.onConsumptionChanged(event))
                .doesNotThrowAnyException();
    }

    // =====================================================================
    // ProductionChangedEvent
    // =====================================================================

    @Test
    void onProductionChanged_validEvent_doesNotThrow() {
        ProductionChangedEvent event = new ProductionChangedEvent(
                BUILDING_ID,
                DEVICE_ID,
                new Energy(BigDecimal.ZERO, EnergyUnit.kW),
                new Energy(new BigDecimal("60"), EnergyUnit.kW)
        );

        assertThatCode(() -> handler.onProductionChanged(event))
                .doesNotThrowAnyException();
    }

    @Test
    void onProductionChanged_zeroToZero_doesNotThrow() {
        ProductionChangedEvent event = new ProductionChangedEvent(
                BUILDING_ID,
                DEVICE_ID,
                new Energy(BigDecimal.ZERO, EnergyUnit.kW),
                new Energy(BigDecimal.ZERO, EnergyUnit.kW)
        );

        assertThatCode(() -> handler.onProductionChanged(event))
                .doesNotThrowAnyException();
    }
}
