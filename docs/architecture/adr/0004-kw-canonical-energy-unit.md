# ADR-0004: kW as Canonical Energy Unit

**Status:** Accepted  
**Date:** 2026-05-04

## Context

Energy values enter the system in different units: clients may send `kW`, `MW`, or `W`.
Domain invariants (e.g. `consumption ≤ totalProductionRate`) require comparing values that may
arrive in different units. Without normalization, every comparison would need explicit
unit conversion at the point of use.

## Decision

**kW is the canonical internal unit.** All energy arithmetic and invariant checks operate
in kW. Conversion is done inside the `Energy` value object.

Key implications:
- `Energy.to(EnergyUnit)` performs unit conversion and returns a **new** `Energy` instance —
  `Energy` is immutable (all fields `final`, enforced by ArchUnit rule 10); no mutation occurs
- `Energy.greaterThan()` converts both operands to kW via `to()` before comparing
- `Energy.equals()` and `hashCode()` normalize to kW before hashing — two `Energy` objects
  with the same physical quantity but different units are considered equal
- `PublicBuilding.calculateTotalProductionRate()` calls `.to(EnergyUnit.kW).value()` on each device's
  production rate and sums the results — all arithmetic stays in kW
- New buildings start with `Energy(0, kW)`

The `EnergyUnit` enum provides the `toKw(BigDecimal)` conversion factor for each unit.

```java
// Energy.compareTo(Energy other) {
BigDecimal thisKw = unit.toKW(value);
BigDecimal otherKw = other.unit.toKW(other.value);
return thisKw.compareTo(otherKw);
```

## Consequences

**Positive:**
- Invariant checks are unit-agnostic — the domain never needs to know what unit the client used
- `Energy.equals()` correctly handles semantic equality across units
- Canonical unit is explicit and documented — no implicit assumptions in business logic

**Negative:**
- kW is a power unit, not energy (kWh would be more correct for consumption accounting).
  This is an accepted simplification for the current scope.
- Callers storing the original input unit receive it back in responses, but internally
  all calculations use kW regardless
