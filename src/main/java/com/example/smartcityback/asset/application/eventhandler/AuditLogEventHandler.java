package com.example.smartcityback.asset.application.eventhandler;

import com.example.smartcityback.asset.domain.event.BuildingCreatedEvent;
import com.example.smartcityback.asset.domain.event.BuildingDeletedEvent;
import com.example.smartcityback.asset.domain.event.ConsumptionChangedEvent;
import com.example.smartcityback.asset.domain.event.DeviceAddedEvent;
import com.example.smartcityback.asset.domain.event.DeviceRemovedEvent;
import com.example.smartcityback.asset.domain.event.ProductionChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Audit events are written to the application log only — there is no separate audit_log
// database table or event store. This is a deliberate design choice for the current scale:
// logs are structured (key=value format), correlated by requestId via MDC, and retained by
// the deployment platform. If compliance requirements or queryable audit history become
// necessary, this handler should be extended to persist events to a dedicated audit table
// or forward them to an external audit service (e.g. an event stream or SIEM system).
//
// All handlers use @TransactionalEventListener(phase = AFTER_COMMIT), not plain @EventListener.
// PublicBuildingAppService publishes these events from inside its own @Transactional method,
// right after repository.save(...) but before the transaction commits. A plain @EventListener
// fires synchronously at publish time, inside that same transaction — an exception thrown here
// would propagate back through publishEvent() and could roll back the aggregate save itself, and
// a successful log line could describe a write that later fails to commit. AFTER_COMMIT guarantees
// an audit entry is only ever written for a change that is durably persisted.
@Component
@Slf4j
public class AuditLogEventHandler {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBuildingCreated(BuildingCreatedEvent event) {
        log.info("AUDIT BuildingCreated buildingId={} name={} location={}",
                event.buildingId(),
                event.name(),
                event.location());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBuildingDeleted(BuildingDeletedEvent event) {
        log.info("AUDIT BuildingDeleted buildingId={} name={}", event.buildingId(), event.name());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeviceAdded(DeviceAddedEvent event) {
        log.info("AUDIT DeviceAdded buildingId={} deviceId={} deviceName={} deviceType={}",
                event.buildingId(),
                event.deviceId(),
                event.deviceName(),
                event.deviceType());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeviceRemoved(DeviceRemovedEvent event) {
        log.info("AUDIT DeviceRemoved buildingId={} deviceId={} deviceName={} deviceType={}",
                event.buildingId(),
                event.deviceId(),
                event.deviceName(),
                event.deviceType());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConsumptionChanged(ConsumptionChangedEvent event) {
        log.info("AUDIT ConsumptionChanged buildingId={} from={} to={}",
                event.buildingId(),
                event.oldConsumption(),
                event.newConsumption());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductionChanged(ProductionChangedEvent event) {
        log.info("AUDIT ProductionChanged buildingId={} deviceId={} from={} to={}",
                event.buildingId(),
                event.deviceId(),
                event.oldProduction(),
                event.newProduction());
    }
}
