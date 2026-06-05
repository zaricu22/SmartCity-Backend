package com.example.smartcityback.asset.domain.shared.enums;

// Error codes live in the domain layer so that domain exceptions can reference them without
// importing anything from the application or infrastructure layers. This is a shared-kernel
// trade-off for a single bounded context: a handful of codes such as BUILDING_NOT_FOUND and
// DEVICE_NOT_FOUND are semantically owned by the application layer, but centralising them
// here avoids splitting a small enum across two layers for no practical gain. If the bounded
// context grows and error handling diverges between layers, this enum should be split.
public enum ErrorCode {
    DEVICE_CAPACITY_REQUIRED,
    DEVICE_CAPACITY_ZERO,
    DEVICE_CAPACITY_NEGATIVE,
    DEVICE_CAPACITY_OUT_OF_RANGE,
    DEVICE_TYPE_REQUIRED,
    DEVICE_ALREADY_EXISTS,
    DEVICE_NOT_FOUND,

    ENERGY_VALUE_REQURIED,
    ENERGY_UNIT_REQUIRED,
    ENERGY_NEGATIVE,

    TOTAL_CAPACITY_EXCEEDED,

    BUILDING_NAME_EMPTY,
    BUILDING_ADDRESS_EMPTY,
    BUILDING_ALREADY_EXISTS,
    BUILDING_NOT_FOUND,

}
