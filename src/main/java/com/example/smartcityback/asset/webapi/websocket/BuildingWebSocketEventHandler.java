package com.example.smartcityback.asset.webapi.websocket;

import com.example.smartcityback.asset.domain.event.BuildingCreatedEvent;
import com.example.smartcityback.asset.domain.event.ConsumptionChangedEvent;
import com.example.smartcityback.asset.domain.event.DeviceAddedEvent;
import com.example.smartcityback.asset.domain.event.ProductionChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * WebSocket event handler — bridges domain events to connected WebSocket clients.
 *
 * Listens to domain events published by PublicBuildingAppService and pushes
 * real-time updates to frontend clients subscribed to the relevant topics.
 *
 * Topics:
 *   /topic/buildings                           — building creation (collection-level; no
 *                                                 per-id subscriber can exist before creation)
 *   /topic/buildings/{buildingId}/consumption  — consumption change updates
 *   /topic/buildings/{buildingId}/devices      — device addition updates
 *   /topic/buildings/{buildingId}/production   — device production rate updates
 *
 * This handler lives in webapi because it depends on SimpMessagingTemplate,
 * which is a web/infrastructure concern. Domain events remain pure — they have
 * no knowledge of WebSocket.
 *
 * Frontend subscription example (STOMP/SockJS):
 *   client.subscribe('/topic/buildings/550e8400-.../consumption', (msg) => {
 *       const update = JSON.parse(msg.body);
 *       dashboard.updateConsumption(update.buildingId, update.newValue, update.newUnit);
 *   });
 */
@Component
@Slf4j
public class BuildingWebSocketEventHandler {

    private final SimpMessagingTemplate messagingTemplate;

    public BuildingWebSocketEventHandler(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @EventListener
    public void onBuildingCreated(BuildingCreatedEvent event) {
        BuildingCreatedMessage message = new BuildingCreatedMessage(
                event.buildingId(),
                event.name(),
                event.location()
        );

        messagingTemplate.convertAndSend("/topic/buildings", message);

        log.debug("WebSocket pushed BuildingCreated buildingId={} topic=/topic/buildings", event.buildingId());
    }

    @EventListener
    public void onConsumptionChanged(ConsumptionChangedEvent event) {
        String topic = "/topic/buildings/" + event.buildingId() + "/consumption";

        ConsumptionUpdateMessage message = new ConsumptionUpdateMessage(
                event.buildingId(),
                event.oldConsumption().value(),
                event.oldConsumption().unit(),
                event.newConsumption().value(),
                event.newConsumption().unit()
        );

        messagingTemplate.convertAndSend(topic, message);

        log.debug("WebSocket pushed ConsumptionUpdate buildingId={} topic={}",
                event.buildingId(), topic);
    }

    @EventListener
    public void onDeviceAdded(DeviceAddedEvent event) {
        String topic = "/topic/buildings/" + event.buildingId() + "/devices";

        DeviceAddedMessage message = new DeviceAddedMessage(
                event.buildingId(),
                event.deviceId(),
                event.deviceType()
        );

        messagingTemplate.convertAndSend(topic, message);

        log.debug("WebSocket pushed DeviceAdded buildingId={} topic={}",
                event.buildingId(), topic);
    }

    @EventListener
    public void onProductionChanged(ProductionChangedEvent event) {
        String topic = "/topic/buildings/" + event.buildingId() + "/production";

        ProductionUpdateMessage message = new ProductionUpdateMessage(
                event.buildingId(),
                event.deviceId(),
                event.oldProduction().value(),
                event.oldProduction().unit(),
                event.newProduction().value(),
                event.newProduction().unit()
        );

        messagingTemplate.convertAndSend(topic, message);

        log.debug("WebSocket pushed ProductionUpdate buildingId={} deviceId={} topic={}",
                event.buildingId(), event.deviceId(), topic);
    }
}
