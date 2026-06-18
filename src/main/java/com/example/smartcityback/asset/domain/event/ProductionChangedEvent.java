package com.example.smartcityback.asset.domain.event;

import com.example.smartcityback.asset.domain.valueobject.Energy;

import java.util.UUID;

public record ProductionChangedEvent(UUID buildingId, UUID deviceId, Energy oldProduction, Energy newProduction) implements DomainEvent {}
