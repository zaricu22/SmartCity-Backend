package com.example.smartcityback.asset.webapi.websocket;

import com.example.smartcityback.asset.domain.event.ConsumptionChangedEvent;
import com.example.smartcityback.asset.domain.event.DeviceAddedEvent;
import com.example.smartcityback.asset.domain.shared.enums.DeviceType;
import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;
import com.example.smartcityback.asset.domain.valueobject.Energy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BuildingWebSocketEventHandlerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private BuildingWebSocketEventHandler handler;

    private static final UUID BUILDING_ID = UUID.randomUUID();
    private static final UUID DEVICE_ID   = UUID.randomUUID();

    // =====================================================================
    // ConsumptionChangedEvent
    // =====================================================================

    @Test
    void onConsumptionChanged_publishesToCorrectTopic() {
        ConsumptionChangedEvent event = new ConsumptionChangedEvent(
                BUILDING_ID,
                new Energy(BigDecimal.ZERO, EnergyUnit.kW),
                new Energy(new BigDecimal("50"), EnergyUnit.kW)
        );

        handler.onConsumptionChanged(event);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/buildings/" + BUILDING_ID + "/consumption"),
                any(ConsumptionUpdateMessage.class)
        );
    }

    @Test
    void onConsumptionChanged_messageContainsCorrectValues() {
        ConsumptionChangedEvent event = new ConsumptionChangedEvent(
                BUILDING_ID,
                new Energy(BigDecimal.ZERO, EnergyUnit.kW),
                new Energy(new BigDecimal("50"), EnergyUnit.kW)
        );

        ArgumentCaptor<ConsumptionUpdateMessage> captor =
                ArgumentCaptor.forClass(ConsumptionUpdateMessage.class);

        handler.onConsumptionChanged(event);

        verify(messagingTemplate).convertAndSend(any(String.class), captor.capture());

        ConsumptionUpdateMessage message = captor.getValue();
        assertThat(message.buildingId()).isEqualTo(BUILDING_ID);
        assertThat(message.oldValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(message.oldUnit()).isEqualTo(EnergyUnit.kW);
        assertThat(message.newValue()).isEqualByComparingTo("50");
        assertThat(message.newUnit()).isEqualTo(EnergyUnit.kW);
    }

    // =====================================================================
    // DeviceAddedEvent
    // =====================================================================

    @Test
    void onDeviceAdded_publishesToCorrectTopic() {
        DeviceAddedEvent event = new DeviceAddedEvent(BUILDING_ID, DEVICE_ID, DeviceType.SOLAR);

        handler.onDeviceAdded(event);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/buildings/" + BUILDING_ID + "/devices"),
                any(DeviceAddedMessage.class)
        );
    }

    @Test
    void onDeviceAdded_messageContainsCorrectValues() {
        DeviceAddedEvent event = new DeviceAddedEvent(BUILDING_ID, DEVICE_ID, DeviceType.BATTERY);

        ArgumentCaptor<DeviceAddedMessage> captor =
                ArgumentCaptor.forClass(DeviceAddedMessage.class);

        handler.onDeviceAdded(event);

        verify(messagingTemplate).convertAndSend(any(String.class), captor.capture());

        DeviceAddedMessage message = captor.getValue();
        assertThat(message.buildingId()).isEqualTo(BUILDING_ID);
        assertThat(message.deviceId()).isEqualTo(DEVICE_ID);
        assertThat(message.deviceType()).isEqualTo(DeviceType.BATTERY);
    }
}
