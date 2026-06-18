package com.example.smartcityback.asset.application.eventhandler;

import com.example.smartcityback.asset.domain.event.ConsumptionChangedEvent;
import com.example.smartcityback.asset.domain.event.DeviceAddedEvent;
import com.example.smartcityback.asset.domain.event.ProductionChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// Audit events are written to the application log only — there is no separate audit_log
// database table or event store. This is a deliberate design choice for the current scale:
// logs are structured (key=value format), correlated by requestId via MDC, and retained by
// the deployment platform. If compliance requirements or queryable audit history become
// necessary, this handler should be extended to persist events to a dedicated audit table
// or forward them to an external audit service (e.g. an event stream or SIEM system).
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

    @EventListener
    public void onProductionChanged(ProductionChangedEvent event) {
        log.info("AUDIT ProductionChanged buildingId={} deviceId={} from={} to={}",
                event.buildingId(),
                event.deviceId(),
                event.oldProduction(),
                event.newProduction());
    }
}
