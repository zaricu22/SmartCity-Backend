package com.example.smartcityback.asset.domain.event;

/**
 * Marker interface for all domain events raised by aggregates in the asset bounded context.
 * Every event that an aggregate adds to its internal event list must implement this interface
 * so that the list is typed as {@code List<DomainEvent>} rather than {@code List<Object>}.
 * This enforces at compile time that only genuine domain events — not arbitrary objects —
 * can be raised and later dispatched via {@code ApplicationEventPublisher}.
 */
public interface DomainEvent {}
