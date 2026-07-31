-- SmartCity Backend — database initialisation script
-- Creates the database, schema, tables, and constraints from scratch.
--
-- Target:  CockroachDB / PostgreSQL — uses three-part names (smartcity.public.table).
--          MySQL:  replace three-part names (smartcity.public.table) with two-part (smartcity.table),
--                  replace UUID with CHAR(36).
--
-- Run in two steps separately to avoid errors:

-- ============================================================
-- Database
-- ============================================================

CREATE DATABASE IF NOT EXISTS smartcity;

-- ============================================================
-- Schema
-- ============================================================

CREATE SCHEMA IF NOT EXISTS public;

-- ============================================================
-- Tables
-- ============================================================

CREATE TABLE IF NOT EXISTS smartcity.public.public_building (
    id                 UUID           PRIMARY KEY,  -- MySQL: CHAR(36)
    name               TEXT           NOT NULL,
    location           TEXT           NOT NULL,
    consumption_value  NUMERIC(19,4)  NOT NULL,     -- @AttributeOverride(name="value",  column=@Column(name="consumption_value"))
    consumption_unit   VARCHAR(10)    NOT NULL,     -- @AttributeOverride(name="unit",   column=@Column(name="consumption_unit"))

    -- total_capacity_value and total_capacity_unit are intentionally NOT stored.
    -- Total capacity is computed at runtime by summing rated_capacity_value across all
    -- energy_device rows for the building. Storing it would require keeping it in sync
    -- with every device insert/delete and is unnecessary at current scale.
    -- total_capacity_value NUMERIC(19,4) NOT NULL,
    -- total_capacity_unit  VARCHAR(10)    NOT NULL,

    version            BIGINT         NOT NULL      -- optimistic locking (@Version)
);

CREATE TABLE IF NOT EXISTS smartcity.public.energy_device (
    id                    UUID           PRIMARY KEY,  -- MySQL: CHAR(36)
    building_id           UUID           NOT NULL,     -- MySQL: CHAR(36)
    name                  TEXT           NOT NULL,
    type                  VARCHAR(50)    NOT NULL,
    rated_capacity_value  NUMERIC(19,4)  NOT NULL,     -- @AttributeOverride(name="value", column=@Column(name="rated_capacity_value"))
    rated_capacity_unit   VARCHAR(10)    NOT NULL,     -- @AttributeOverride(name="unit",  column=@Column(name="rated_capacity_unit"))
    production_value      NUMERIC(19,4)  NOT NULL,     -- @AttributeOverride(name="value", column=@Column(name="production_value"))
    production_unit       VARCHAR(10)    NOT NULL,     -- @AttributeOverride(name="unit",  column=@Column(name="production_unit"))

    CONSTRAINT fk_building
        FOREIGN KEY (building_id)
        REFERENCES smartcity.public.public_building(id)
        ON DELETE CASCADE  -- cascade = CascadeType.ALL, orphanRemoval = true
);

-- ============================================================
-- Constraints
-- ============================================================

-- Value constraints
ALTER TABLE smartcity.public.public_building
    ADD CONSTRAINT chk_consumption_value_non_negative
    CHECK (consumption_value >= 0);

ALTER TABLE smartcity.public.energy_device
    ADD CONSTRAINT chk_rated_capacity_positive
    CHECK (rated_capacity_value > 0);

ALTER TABLE smartcity.public.energy_device
    ADD CONSTRAINT chk_production_non_negative
    CHECK (production_value >= 0);

-- Unit enum constraints
ALTER TABLE smartcity.public.public_building
    ADD CONSTRAINT chk_consumption_unit
    CHECK (consumption_unit IN ('kW', 'MW', 'GW'));

ALTER TABLE smartcity.public.energy_device
    ADD CONSTRAINT chk_rated_capacity_unit
    CHECK (rated_capacity_unit IN ('kW', 'MW', 'GW'));

ALTER TABLE smartcity.public.energy_device
    ADD CONSTRAINT chk_production_unit
    CHECK (production_unit IN ('kW', 'MW', 'GW'));
