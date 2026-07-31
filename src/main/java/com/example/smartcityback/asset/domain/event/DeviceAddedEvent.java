package com.example.smartcityback.asset.domain.event;

import com.example.smartcityback.asset.domain.shared.enums.DeviceType;

import java.util.UUID;

public record DeviceAddedEvent(UUID buildingId, UUID deviceId, String deviceName, DeviceType deviceType) implements DomainEvent {}
