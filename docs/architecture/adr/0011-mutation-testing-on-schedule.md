# ADR-0011: Mutation Testing on Schedule, Not Every Push

**Status:** Accepted  
**Date:** 2026-06-02

## Context

PIT mutation testing verifies that unit tests actually catch domain logic regressions —
it mutates bytecode and checks whether tests fail. It is the strongest quality gate
for domain invariant tests.

The problem: PIT is slow. Running it on every push (in the main CI pipeline) adds
several minutes to every build, blocking fast feedback for developers.

## Decision

Mutation testing runs in a **dedicated workflow** (`.github/workflows/mutation-testing.yml`),
separate from the main CI/CD pipeline, triggered in two ways:
- **Scheduled:** weekly on Monday at 02:00 UTC (`cron: '0 2 * * 1'`)
- **Manual:** `workflow_dispatch` for on-demand runs before a release or after major refactors

The `mutation-testing` Maven profile activates PIT. The CI command bypasses the full
`verify` lifecycle (no Surefire re-run, no packaging) to run as fast as possible:

```
mvn test-compile org.pitest:pitest-maven:1.9.11:mutationCoverage -Pmutation-testing
```

**Thresholds** (build fails if not met):
- Mutation score: **65%** (`mutationThreshold`)
- Line coverage: **75%** (`coverageThreshold`)

These are intentionally modest. Projects with a mature testing culture typically target 80%+
mutation and 85%+ line coverage. The lower values reflect the current stage: mutation testing
is being adopted incrementally, and setting the bar too high would block development before
the test suite matures. These are a minimum quality floor, not a target ceiling.

**Excluded from mutation** — classes where mutations are meaningless:
- `*.dto.*`, `*.mapper.*`, `*.request.*`, `*.response.*` — pure data carriers with no
  business logic; mutating a getter return or a field assignment produces surviving mutants
  that indicate nothing about domain correctness
- `*.exception.*` — exception constructors only; no branching or invariants to kill
- `*.repository.*` — Spring Data JPA interfaces; no bytecode implementation to mutate
- `*.config.*` — Spring wiring only; mutations here test framework behavior, not domain rules
- `*.generated.*` — generated code; mutations are meaningless and not our responsibility

**Excluded test classes** — PIT runs every surviving mutant against the full test suite.
Including slow tests means each of hundreds of mutants triggers a full Spring context
startup, Testcontainers launch, or WireMock server — turning a ~5 min run into hours:
- `*IntegrationTest`, `*TransactionalTest`, `*WireMockTest`, `*APITest`
- `*WebSocket*`, `*FullIntegrationTest`, `*ApplicationTests`, `*RepositoryImplTest`

The PIT HTML report is uploaded as a `pit-report` GitHub Actions artifact (from
`target/pit-reports`) and is available for download after every run regardless of pass/fail
(`if: always()`).

## Consequences

**Positive:**
- Fast CI feedback on every push — mutation testing does not block the developer loop
- Scheduled runs still catch regression in mutation coverage over time

**Negative:**
- A PR that breaks mutation coverage will not fail CI immediately — the gap is only
  detected on the next scheduled run
- Mutation coverage is not a merge gate — a developer can merge code with weak tests
  without knowing until the next schedule fires
