package com.example.smartcityback.asset.application.eventhandler;

import com.example.smartcityback.asset.domain.event.ConsumptionChangedEvent;
import com.example.smartcityback.asset.domain.event.DeviceAddedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AuditLogEventHandler {

    @EventListener
    public void onDeviceAdded(DeviceAddedEvent event) {
        log.info("AUDIT DeviceAdded buildingId={} deviceId={} deviceType={}",
                event.buildingId(),
                event.deviceId(),
                event.deviceType());
    }

    @EventListener
    public void onConsumptionChanged(ConsumptionChangedEvent event) {
        log.info("AUDIT ConsumptionChanged buildingId={} from={} to={}",
                event.buildingId(),
                event.oldConsumption(),
                event.newConsumption());
    }
}
