# ADR-0006: CockroachDB for Production

**Status:** Accepted  
**Date:** 2026-05-04

## Context

The application needs a relational database that is:
- PostgreSQL wire-compatible (Spring Data JPA + Hibernate work without modification)
- Highly available for production deployments on GCP

## Decision

**CockroachDB** (GCP Europe West 1) is used for the `prod` profile only.  
All other profiles use lighter-weight alternatives appropriate for their purpose.

### Profile database matrix

| Profile | Database | How configured |
|---|---|---|
| `prod` | CockroachDB on GCP (PostgreSQL wire protocol) | `${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}` env vars; explicit `org.postgresql.Driver` + Hibernate LOB non-contextual-creation flag required for CockroachDB compatibility |
| `dev` | Developer-supplied — typically local CockroachDB or PostgreSQL | `${DB_URL}` env var; developer sets it to whatever instance they run locally |
| `ci` | H2 in-memory (`create-drop`) | Hardcoded in `application-ci.properties`; used for OpenAPI enrichment script and schema generation in CI without a real database instance |
| `test` | PostgreSQL via Testcontainers | Datasource URL injected at runtime by `@DynamicPropertySource`; `create-drop` DDL; ensures tests run against a real PostgreSQL engine |

CockroachDB exposes the PostgreSQL wire protocol, so the JDBC driver, JPA mappings,
and Hibernate dialect require no changes between `dev` and `prod`. The `prod` profile
explicitly sets `org.postgresql.Driver` and `hibernate.jdbc.lob.non_contextual_creation=true`
to avoid a Hibernate LOB context error specific to the CockroachDB + PostgreSQL driver
combination.

Credentials for `dev` and `prod` are supplied exclusively via environment variables —
no credentials are stored in property files.

## Consequences

**Positive:**
- PostgreSQL wire compatibility means zero code changes between dev and prod database engines
- CockroachDB provides distributed SQL, automatic replication, and geo-distribution on GCP
- H2 in `ci` profile keeps the CI pipeline fast and infrastructure-free for non-integration jobs
- Testcontainers in `test` profile gives integration tests a real PostgreSQL engine with full
  schema fidelity — no H2 dialect quirks in test assertions

**Negative:**
- CockroachDB has subtle SQL dialect differences (sequence behaviour, some DDL constraints)
  that can surface at the schema level despite wire compatibility
- `test` profile uses PostgreSQL (Testcontainers), `ci` profile uses H2 — a schema valid
  in H2 may behave differently in PostgreSQL; the two profiles serve different purposes and
  are not interchangeable
- Local `dev` setup is flexible but undocumented — developers must know to point `${DB_URL}`
  at a running CockroachDB or PostgreSQL instance
