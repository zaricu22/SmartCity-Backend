package com.example.smartcityback.asset.domain.event;

import java.util.UUID;

public record BuildingCreatedEvent(UUID buildingId, String name, String location) implements DomainEvent {}
