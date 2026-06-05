package com.example.smartcityback.asset.domain.shared.enums;

// Intentionally closed to three types that cover the current smart-city asset scope:
// SOLAR (photovoltaic panels), PUMP (water/heat pumps), BATTERY (storage units).
// Adding a new device type is not a data-only change — it requires updating this enum,
// reviewing any type-dispatch logic in the domain, and providing a database migration
// for existing stored string values. Do not add values without a corresponding ADR.
public enum DeviceType {
    SOLAR,
    PUMP,
    BATTERY
}
