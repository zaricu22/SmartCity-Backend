# ADR-0010: AI-Enriched OpenAPI Spec

**Status:** Accepted  
**Date:** 2026-06-02

## Context

Auto-generated OpenAPI specs from Springdoc lack descriptions and examples — they are
structurally correct but not human-readable. Writing full `@Operation`, `@Schema`, and
`@ApiResponse` annotations manually for every endpoint is repetitive.

Two extremes:
- **Fully manual** — complete annotation coverage in code; most accurate, high effort
- **Fully AI-generated** — AI writes the entire spec independently of code; every code change
  (new endpoint, renamed field, changed response model) makes the spec stale — someone must
  manually re-trigger generation or hand-edit the spec to keep it in sync, compounding over time

## Decision

**Code annotations are the primary source of truth. AI fills what is missing.**

A Python script (`scripts/enrich_openapi.py`) runs in CI after Springdoc generates
the raw spec and before the deploy job:

1. Springdoc generates `openapi.json` from code annotations
2. Each schema/operation of new `openapi.json` is hashed(`x-raw-hash`) 
    and compared to schema/operation hash of previous `openapi.json` (stored in last enriched file)
3. If the hash matches and all desired fields (description, example...) are present → 
    skip (no API call)
4. If the hash differs (code changed) or desired fields missing → 
    Claude enriches only that part (schema/operation) of new `openapi.json` file,
    preserving everything what the code (developer) already provides, 
    adding only missing examples and descriptions
5. The enriched file is committed to `main` before deploy — the deployed JAR serves it

**Precedence rule:** `@Operation`, `@Schema`, `@ApiResponse` annotations always win.
Claude never overrides what the developer has already written or changed.

The enriched file is stored at `src/main/resources/static/openapi/enhanced-openapi.json`
and served as a static resource by the deployed JAR. Swagger UI is configured to load it
instead of the raw auto-generated spec (`springdoc.swagger-ui.url` in `application.properties`).

The script calls **Claude Sonnet 4.6** (`claude-sonnet-4-6`) for enrichment. If the model
version is upgraded, the enriched file should be regenerated and reviewed — model changes
can affect description style, verbosity, and accuracy.

## Consequences

**Positive:**
- Swagger UI is human-readable without requiring full annotation coverage in code
- Incremental enrichment — only changed parts hit the Claude API, reducing cost
- Code annotations still work and take precedence — no lock-in to AI-generated content

**Negative:**
- Enriched file is committed to source control — two representations of the same API
- If enrichment script fails, the deploy job must decide whether to proceed with the
  stale enriched file or fail the pipeline
- AI-generated descriptions can be inaccurate — requires periodic review
