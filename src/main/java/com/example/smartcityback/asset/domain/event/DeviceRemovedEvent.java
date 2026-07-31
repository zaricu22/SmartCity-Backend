package com.example.smartcityback.asset.domain.event;

import java.util.UUID;

public record DeviceRemovedEvent(UUID buildingId, UUID deviceId) implements DomainEvent {}
