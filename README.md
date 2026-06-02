# SmartCity Backend

![Build](https://img.shields.io/github/actions/workflow/status/zaricu22/SmartCity-Backend/ci-cd.yml?branch=main)
![Java](https://img.shields.io/badge/Java-17-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-green)
![License](https://img.shields.io/github/license/zaricu22/SmartCity-Backend)

<!-- YOUR: 2-3 sentences — what is this system, what problem does it solve, who uses it -->

---

## Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Getting Started](#getting-started)
- [Environment Variables](#environment-variables)
- [API Documentation](#api-documentation)
- [Running Tests](#running-tests)
- [Project Structure](#project-structure)
- [License](#license)

---

## Features

<!-- YOUR: bullet list of key features from a product/domain perspective, e.g.:
- Register and manage public buildings
- Attach energy devices (solar panels, pumps, batteries) to buildings
- Track and update energy consumption and production in real time
- WebSocket push notifications on consumption and device changes
- Subsidy eligibility evaluation via domain specifications
-->

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.5 |
| Persistence | Spring Data JPA + PostgreSQL |
| API Docs | Springdoc OpenAPI (Swagger UI) |
| Real-time | WebSocket (STOMP) |
| Build | Maven |
| Containerization | Docker + Docker Compose |
| CI/CD | GitHub Actions |
| Testing | JUnit 5, Testcontainers, WireMock, REST Assured |

---

## Architecture

This project follows **Domain-Driven Design (DDD)** with an **Onion Architecture**, organizing code into concentric layers where outer layers depend on inner ones, never the reverse.

```
┌──────────────────────────────────────────────────────┐
│              Infrastructure / Web API                │  Controllers, WebSocket, JPA, External Clients
│   ┌──────────────────────────────────────────────┐   │
│   │             Application Layer                │   │  Services, Commands, Event Handlers
│   │   ┌──────────────────────────────────────┐   │   │
│   │   │           Domain Layer               │   │   │  Aggregates, Entities, Events, Specs
│   │   └──────────────────────────────────────┘   │   │
│   └──────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────┘
```

<!-- YOUR: optionally add a sentence or two explaining why you chose this architecture or what the main design goals were -->

---

## Getting Started

### Prerequisites

- Java 17+
- Docker & Docker Compose
- Maven 3.9+ (or use the included `./mvnw` wrapper)

### Clone the repository

```bash
git clone https://github.com/zaricu22/SmartCity-Backend.git
cd SmartCity-Backend
```

### Configure environment

Copy the example env file and fill in your values:

```bash
cp .env.example .env
```

> See [Environment Variables](#environment-variables) for required values.

### Run with Docker Compose

```bash
docker-compose up -d
```

### Run locally (dev profile)

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The application starts on `http://localhost:8080/SmartCityREST`.

---

## Environment Variables

| Variable | Description | Example |
|---|---|---|
| `DB_URL` | PostgreSQL JDBC connection URL | `jdbc:postgresql://localhost:5432/smartcity` |
| `DB_USERNAME` | Database username | `admin` |
| `DB_PASSWORD` | Database password | `secret` |

<!-- YOUR: add any additional env vars if present (e.g. external API keys, WebSocket config) -->

### Profiles

| Profile | Purpose |
|---|---|
| `dev` | Local development, port 8080 |
| `staging` | <!-- YOUR: describe staging setup --> |
| `prod` | <!-- YOUR: describe prod setup --> |

---

## API Documentation

Once the application is running, Swagger UI is available at:

```
http://localhost:8080/SmartCityREST/swagger-ui.html
```

> **AI-enriched spec:** Swagger UI is configured to load an enhanced OpenAPI spec from
> `/openapi/enhanced-openapi.json` instead of the raw auto-generated one. This file is
> produced by an AI agentic script that takes the base spec and enriches it with
> descriptions, examples, and additional metadata. See `application.properties` →
> `springdoc.swagger-ui.url`.

### Main endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/v1/buildings` | Create a new public building |
| `GET` | `/v1/buildings/{id}` | Get a building by ID |
| `GET` | `/v1/buildings/all` | Get all buildings |
| `POST` | `/v1/buildings/{id}/devices` | Add an energy device to a building |
| `PUT` | `/v1/buildings/{id}/consumption` | Update building energy consumption |
| `PATCH` | `/v1/buildings/{buildingId}/devices/{deviceId}/production` | Update device production rate |

### WebSocket

```
ws://localhost:8080/SmartCityREST/ws
```

<!-- YOUR: describe WebSocket topics/subscriptions and message formats if you want to document them -->

---

## CI/CD

This project uses **GitHub Actions** for continuous integration and deployment.

| Trigger | Pipeline |
|---|---|
| Push / PR to `main` | Build → Test → <!-- YOUR: deploy step? --> |

Pipeline configuration: [`.github/workflows/ci-cd.yml`](.github/workflows/ci-cd.yml)

Build status is reflected in the badge at the top of this file.

---

## Running Tests

```bash
# All tests
./mvnw test

# Unit tests only
./mvnw test -Dgroups="unit"

# Integration tests only (requires Docker for Testcontainers)
./mvnw test -Dgroups="integration"
```

<!-- YOUR: mention coverage threshold if you want (JaCoCo is configured) -->

---

## Project Structure

```
src/
└── main/
    └── java/com/example/smartcityback/
        ├── asset/
        │   ├── domain/          # Aggregates, entities, domain events, specifications
        │   ├── application/     # Services, commands, event handlers, DTOs
        │   ├── infrastructure/  # JPA repositories, external service clients
        │   └── webapi/          # Controllers, WebSocket, request/response models
        └── config/              # OpenAPI and global configuration
```

---

## License

<!-- YOUR: choose a license — MIT, Apache 2.0, or proprietary -->
Distributed under the [MIT License](LICENSE).
