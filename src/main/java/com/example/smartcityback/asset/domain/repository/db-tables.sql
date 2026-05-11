

CREATE TABLE public_building (
    id UUID PRIMARY KEY, -- MySQL: CHAR(36)
    name TEXT NOT NULL,
    location TEXT NOT NULL,

    consumption_value NUMERIC(19,4) NOT NULL,  -- @AttributeOverride(name="value", column=@Column(name="rated_capacity_value", nullable = false)),
    consumption_unit VARCHAR(10) NOT NULL,     -- @AttributeOverride(name="unit", column=@Column(name="consumption_unit", nullable = false)),

    -- total_capacity_value NUMERIC(19,4) NOT NULL,
    -- total_capacity_unit VARCHAR(10) NOT NULL,

    version BIGINT NOT NULL
);

CREATE TABLE energy_device (
    id UUID PRIMARY KEY, -- MySQL: CHAR(36)
    building_id UUID NOT NULL, -- MySQL: CHAR(36)  -- cascade = CascadeType.ALL, orphanRemoval = true
    type VARCHAR(50) NOT NULL,

    rated_capacity_value NUMERIC(19,4) NOT NULL,  -- @AttributeOverride(name="value", column=@Column(name="rated_capacity_value", nullable = false)),
    rated_capacity_unit VARCHAR(10) NOT NULL,   -- @AttributeOverride(name="unit", column=@Column(name="rated_capacity_value", nullable = false)),

    production_value NUMERIC(19,4) NOT NULL, -- @AttributeOverride(name="value", column=@Column(name="production_value", nullable = false)),
    production_unit VARCHAR(10) NOT NULL,    -- @AttributeOverride(name="unit", column=@Column(name="production_unit", nullable = false)),

    CONSTRAINT fk_building
        FOREIGN KEY (building_id)
        REFERENCES public_building(id)
        ON DELETE CASCADE  -- cascade = CascadeType.ALL, orphanRemoval = true
);

-- values should never be negative
ALTER TABLE public_building
  ADD CONSTRAINT chk_consumption_value_non_negative
  CHECK (consumption_value >= 0);

ALTER TABLE energy_device
  ADD CONSTRAINT chk_rated_capacity_positive
  CHECK (rated_capacity_value > 0);

ALTER TABLE energy_device
  ADD CONSTRAINT chk_production_non_negative
  CHECK (production_value >= 0);

-- unit columns should only contain known enum values
ALTER TABLE public_building
  ADD CONSTRAINT chk_consumption_unit
  CHECK (consumption_unit IN ('kW', 'MW', 'GW'));

ALTER TABLE energy_device
  ADD CONSTRAINT chk_rated_capacity_unit
  CHECK (rated_capacity_unit IN ('kW', 'MW', 'GW'));

ALTER TABLE energy_device
  ADD CONSTRAINT chk_production_unit
  CHECK (production_unit IN ('kW', 'MW', 'GW'));


