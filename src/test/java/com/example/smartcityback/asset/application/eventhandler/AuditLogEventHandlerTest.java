package com.example.smartcityback.asset.application.eventhandler;

import com.example.smartcityback.asset.domain.event.ConsumptionChangedEvent;
import com.example.smartcityback.asset.domain.event.DeviceAddedEvent;
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
}
