# Entity-Relationship Diagram

Database schema for the `asset` bounded context. The `auth` context uses no persistent tables — user registry, refresh tokens, and token blacklist are in-memory.

```mermaid
erDiagram
    public_building {
        char_36  id                  PK
        varchar  name                "NOT NULL"
        varchar  location            "NOT NULL"
        decimal  consumption_value   "NOT NULL"
        varchar  consumption_unit    "NOT NULL (kW / MW / GW)"
        bigint   version             "nullable — optimistic lock (@Version)"
    }

    energy_device {
        char_36  id                    PK
        char_36  building_id           FK
        varchar  type                  "NOT NULL (SOLAR / WIND / HYDRO / ...)"
        decimal  rated_capacity_value  "NOT NULL — immutable after creation"
        varchar  rated_capacity_unit   "NOT NULL"
        decimal  production_value      "NOT NULL"
        varchar  production_unit       "NOT NULL"
    }

    public_building ||--o{ energy_device : "cascade ALL · orphanRemoval · fetch LAZY"
```

## Notes

- IDs are `CHAR(36)` UUID strings — CockroachDB and MySQL have no native UUID type.
- `consumption_value` / `consumption_unit` and the two `Energy` pairs on `energy_device` are mapped via `@Embedded @AttributeOverrides` — each `Energy` value object becomes two flat columns.
- `version` is a nullable `Long` wrapper (not primitive `long`) so that new entities with `null` version can be persisted without Hibernate treating `0` as a first-update conflict.
- `orphanRemoval = true` on the `OneToMany` means deleting a device from `PublicBuilding.devices` in Java automatically issues a `DELETE` — no explicit repository call needed.
- The domain's `PublicBuildingRepository` interface and `PublicBuildingRepositoryImpl` are the only paths through which these tables are accessed; controllers and application services never touch JPA entities directly.
