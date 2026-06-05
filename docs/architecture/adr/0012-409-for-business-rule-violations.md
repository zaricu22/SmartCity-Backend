# ADR-0012: HTTP Status Tier Design — 422 for Validation, 409 for Business Rule Violations

**Status:** Accepted  
**Date:** 2026-05-04

## Context

The API needs a consistent mapping from exception type to HTTP status code. Three distinct
error categories require three distinct codes:

- Input is malformed at the protocol level (unparseable JSON, wrong `Content-Type`)
- Input is syntactically valid but fails validation (missing required field, negative value)
- Input is valid but the operation is blocked by current resource state (domain rule, concurrency)

## Decision

The full HTTP status tier applied in `GlobalExceptionHandler`:

| Status | Meaning | Triggered by |
|---|---|---|
| **400** Bad Request | Malformed at protocol level — unparseable JSON, wrong `Content-Type`, missing body | Spring's `DefaultHandlerExceptionResolver` (`HttpMessageNotReadableException`) — fires before `GlobalExceptionHandler` |
| **422** Unprocessable Entity | Syntactically valid request that fails validation | `MethodArgumentNotValidException` (`@Valid` field constraints) + `ValidationException` (domain structural invariants) |
| **409** Conflict | Valid request blocked by current resource state | `BusinessRuleViolationException` subclasses + `ObjectOptimisticLockingFailureException` |
|---|---|---|
| **404** Not Found | Resource does not exist | `NotFoundException` subclasses |
| **500** Internal Server Error | Unexpected failure | `Exception` fallback |

**Why 422 for both `@Valid` and `ValidationException`:**  
Both represent the same category — the request JSON is parseable and well-structured, but a
field value violates a constraint. 400 is reserved for requests that cannot be parsed at all.
`ValidationException` is thrown by domain constructors (`PublicBuilding`, `Energy`,
`EnergyDevice`) as a last defensive guard; in practice `@Valid` at the web layer catches the same
conditions first, but the domain guard fires if the domain is called from any other entry point.

**Why `BusinessRuleViolationException` and `ObjectOptimisticLockingFailureException` share 409:**  
Both fail not because the input is wrong, but because of the **current state of the resource
at the moment of the request**. In both cases the client's fix is not "correct your input"
but "check current state, then retry or reconsider" — which is exactly what 409 Conflict
means by definition: "conflict with the current state of the resource".
- `BusinessRuleViolationException` — resource is in a state that blocks the operation
  (capacity already full, device already exists)
- `ObjectOptimisticLockingFailureException` — resource was modified by a concurrent request
  between your read and your write; Hibernate detects the `@Version` mismatch on flush

## Consequences

**Positive:**
- Three distinct codes for three distinct error categories — clients apply different UX for each
- 422 vs 409 is unambiguous: 422 = fix your values, 409 = operation not allowed given current state
- 400 is reserved for truly malformed requests, making it a reliable signal

**Negative:**
- `ValidationException` in domain objects is a safety net that never fires through the normal
  web path (providing last defensive barrier) — `@Valid - MethodArgumentNotValidException` always catches the same conditions first. 
  The 422 handler for `ValidationException` is technically reachable only from non-web callers (tests, batch jobs).
