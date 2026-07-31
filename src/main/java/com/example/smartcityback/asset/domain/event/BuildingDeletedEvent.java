package com.example.smartcityback.asset.domain.event;

import java.util.UUID;

public record BuildingDeletedEvent(UUID buildingId) implements DomainEvent {}
