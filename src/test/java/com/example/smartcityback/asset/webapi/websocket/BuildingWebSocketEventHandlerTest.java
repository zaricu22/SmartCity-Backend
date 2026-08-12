package com.example.smartcityback.asset.webapi.websocket;

import com.example.smartcityback.asset.domain.event.BuildingCreatedEvent;
import com.example.smartcityback.asset.domain.event.BuildingDeletedEvent;
import com.example.smartcityback.asset.domain.event.ConsumptionChangedEvent;
import com.example.smartcityback.asset.domain.event.DeviceAddedEvent;
import com.example.smartcityback.asset.domain.event.DeviceRemovedEvent;
import com.example.smartcityback.asset.domain.event.ProductionChangedEvent;
import com.example.smartcityback.asset.domain.shared.enums.DeviceType;
import com.example.smartcityback.asset.domain.shared.enums.EnergyUnit;
import com.example.smartcityback.asset.domain.valueobject.Energy;
import org.junit.jupiter.api.DisplayName;
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
    // BuildingCreatedEvent
    // =====================================================================

    @Test
    @DisplayName("broadcasts a new building to the shared /topic/buildings channel every connected client "
            + "is subscribed to")
    void onBuildingCreated_publishesToCollectionTopic() {
        BuildingCreatedEvent event = new BuildingCreatedEvent(BUILDING_ID, "City Hall", "Main St 1");

        handler.onBuildingCreated(event);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/buildings"),
                any(BuildingCreatedMessage.class)
        );
    }

    @Test
    @DisplayName("includes the building's ID, name, and location in the broadcast message for a newly "
            + "created building")
    void onBuildingCreated_messageContainsCorrectValues() {
        BuildingCreatedEvent event = new BuildingCreatedEvent(BUILDING_ID, "City Hall", "Main St 1");

        ArgumentCaptor<BuildingCreatedMessage> captor =
                ArgumentCaptor.forClass(BuildingCreatedMessage.class);

        handler.onBuildingCreated(event);

        verify(messagingTemplate).convertAndSend(any(String.class), captor.capture());

        BuildingCreatedMessage message = captor.getValue();
        assertThat(message.buildingId()).isEqualTo(BUILDING_ID);
        assertThat(message.name()).isEqualTo("City Hall");
        assertThat(message.location()).isEqualTo("Main St 1");
    }

    // =====================================================================
    // BuildingDeletedEvent
    // =====================================================================

    @Test
    @DisplayName("broadcasts a deleted building to the /topic/buildings/deleted channel")
    void onBuildingDeleted_publishesToCollectionTopic() {
        BuildingDeletedEvent event = new BuildingDeletedEvent(BUILDING_ID, "City Hall");

        handler.onBuildingDeleted(event);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/buildings/deleted"),
                any(BuildingDeletedMessage.class)
        );
    }

    @Test
    @DisplayName("includes the building's ID and name in the broadcast message for a deleted building")
    void onBuildingDeleted_messageContainsCorrectValues() {
        BuildingDeletedEvent event = new BuildingDeletedEvent(BUILDING_ID, "City Hall");

        ArgumentCaptor<BuildingDeletedMessage> captor =
                ArgumentCaptor.forClass(BuildingDeletedMessage.class);

        handler.onBuildingDeleted(event);

        verify(messagingTemplate).convertAndSend(any(String.class), captor.capture());

        assertThat(captor.getValue().buildingId()).isEqualTo(BUILDING_ID);
        assertThat(captor.getValue().name()).isEqualTo("City Hall");
    }

    // =====================================================================
    // ConsumptionChangedEvent
    // =====================================================================

    @Test
    @DisplayName("broadcasts a consumption change to a channel scoped to that specific building's ID, not "
            + "the shared collection channel")
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
    @DisplayName("includes the building's ID and both the old and new consumption values in the broadcast message")
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
    @DisplayName("broadcasts a newly added device to a channel scoped to that specific building's device list")
    void onDeviceAdded_publishesToCorrectTopic() {
        DeviceAddedEvent event = new DeviceAddedEvent(BUILDING_ID, DEVICE_ID, "Test Device", DeviceType.SOLAR);

        handler.onDeviceAdded(event);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/buildings/" + BUILDING_ID + "/devices"),
                any(DeviceAddedMessage.class)
        );
    }

    @Test
    @DisplayName("includes the device's ID, name, and type in the broadcast message for a newly added device")
    void onDeviceAdded_messageContainsCorrectValues() {
        DeviceAddedEvent event = new DeviceAddedEvent(BUILDING_ID, DEVICE_ID, "Test Device", DeviceType.BATTERY);

        ArgumentCaptor<DeviceAddedMessage> captor =
                ArgumentCaptor.forClass(DeviceAddedMessage.class);

        handler.onDeviceAdded(event);

        verify(messagingTemplate).convertAndSend(any(String.class), captor.capture());

        DeviceAddedMessage message = captor.getValue();
        assertThat(message.buildingId()).isEqualTo(BUILDING_ID);
        assertThat(message.deviceId()).isEqualTo(DEVICE_ID);
        assertThat(message.deviceName()).isEqualTo("Test Device");
        assertThat(message.deviceType()).isEqualTo(DeviceType.BATTERY);
    }

    // =====================================================================
    // DeviceRemovedEvent
    // =====================================================================

    @Test
    @DisplayName("broadcasts a removed device to a channel scoped to that specific building's removed-devices list")
    void onDeviceRemoved_publishesToCorrectTopic() {
        DeviceRemovedEvent event = new DeviceRemovedEvent(BUILDING_ID, DEVICE_ID, "Test Device", DeviceType.SOLAR);

        handler.onDeviceRemoved(event);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/buildings/" + BUILDING_ID + "/devices/removed"),
                any(DeviceRemovedMessage.class)
        );
    }

    @Test
    @DisplayName("includes the device's ID, name, and type in the broadcast message for a removed device")
    void onDeviceRemoved_messageContainsCorrectValues() {
        DeviceRemovedEvent event = new DeviceRemovedEvent(BUILDING_ID, DEVICE_ID, "Test Device", DeviceType.BATTERY);

        ArgumentCaptor<DeviceRemovedMessage> captor =
                ArgumentCaptor.forClass(DeviceRemovedMessage.class);

        handler.onDeviceRemoved(event);

        verify(messagingTemplate).convertAndSend(any(String.class), captor.capture());

        DeviceRemovedMessage message = captor.getValue();
        assertThat(message.buildingId()).isEqualTo(BUILDING_ID);
        assertThat(message.deviceId()).isEqualTo(DEVICE_ID);
        assertThat(message.deviceName()).isEqualTo("Test Device");
        assertThat(message.deviceType()).isEqualTo(DeviceType.BATTERY);
    }

    // =====================================================================
    // ProductionChangedEvent
    // =====================================================================

    @Test
    @DisplayName("broadcasts a production change to a channel scoped to that specific building and device")
    void onProductionChanged_publishesToCorrectTopic() {
        ProductionChangedEvent event = new ProductionChangedEvent(
                BUILDING_ID,
                DEVICE_ID,
                new Energy(BigDecimal.ZERO, EnergyUnit.kW),
                new Energy(new BigDecimal("60"), EnergyUnit.kW)
        );

        handler.onProductionChanged(event);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/buildings/" + BUILDING_ID + "/production"),
                any(ProductionUpdateMessage.class)
        );
    }

    @Test
    @DisplayName("includes the device's ID and both the old and new production values in the broadcast message")
    void onProductionChanged_messageContainsCorrectValues() {
        ProductionChangedEvent event = new ProductionChangedEvent(
                BUILDING_ID,
                DEVICE_ID,
                new Energy(BigDecimal.ZERO, EnergyUnit.kW),
                new Energy(new BigDecimal("60"), EnergyUnit.kW)
        );

        ArgumentCaptor<ProductionUpdateMessage> captor =
                ArgumentCaptor.forClass(ProductionUpdateMessage.class);

        handler.onProductionChanged(event);

        verify(messagingTemplate).convertAndSend(any(String.class), captor.capture());

        ProductionUpdateMessage message = captor.getValue();
        assertThat(message.buildingId()).isEqualTo(BUILDING_ID);
        assertThat(message.deviceId()).isEqualTo(DEVICE_ID);
        assertThat(message.oldValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(message.oldUnit()).isEqualTo(EnergyUnit.kW);
        assertThat(message.newValue()).isEqualByComparingTo("60");
        assertThat(message.newUnit()).isEqualTo(EnergyUnit.kW);
    }
}
