package com.example.smartcityback.asset.domain.event;

import com.example.smartcityback.asset.domain.shared.enums.DeviceType;

import java.util.UUID;

public record DeviceRemovedEvent(UUID buildingId, UUID deviceId, DeviceType deviceType) implements DomainEvent {}
