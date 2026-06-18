-- SmartCity Backend — sample seed data
-- Requires init.sql to have been applied first.
-- 20 buildings · 76 devices total · all values satisfy domain invariants.
--
-- Target:  CockroachDB / PostgreSQL — uses three-part names (database.schema.table)
--          MySQL:  replace three-part names (smartcity.public.table) with two-part (smartcity.table),
--                  replace version keyword with `version` (backticks).

-- ============================================================
-- WIPE ALL EXISTING DATA
-- ============================================================
-- TRUNCATE TABLE smartcity.public.energy_device, smartcity.public.public_building CASCADE;

-- ============================================================
-- Buildings
-- version=0 means no optimistic-lock conflicts on first write
-- consumption_value must not exceed sum of device rated capacities
-- ============================================================

INSERT INTO smartcity.public.public_building (id, name, location, consumption_value, consumption_unit, version) VALUES
    ('a1000000-0000-0000-0000-000000000001', 'City Hall',                  'Main Street 1, Downtown',           320.0000,  'kW', 0),
    ('a1000000-0000-0000-0000-000000000002', 'Public Library',             'Oak Avenue 14, Midtown',             95.0000,  'kW', 0),
    ('a1000000-0000-0000-0000-000000000003', 'Sports Arena',               'Stadium Road 1, West District',     850.0000,  'kW', 0),
    ('a1000000-0000-0000-0000-000000000004', 'Central Hospital',           'Health Boulevard 5, Eastside',      720.0000,  'kW', 0),
    ('a1000000-0000-0000-0000-000000000005', 'North District School',      'Pine Street 22, Northside',          65.0000,  'kW', 0),
    ('a1000000-0000-0000-0000-000000000006', 'Police Station',             'Justice Avenue 3, Downtown',        140.0000,  'kW', 0),
    ('a1000000-0000-0000-0000-000000000007', 'Fire Station No. 2',         'Rescue Boulevard 8, South Quarter', 110.0000,  'kW', 0),
    ('a1000000-0000-0000-0000-000000000008', 'Community Center',           'Elm Street 45, Midtown',            180.0000,  'kW', 0),
    ('a1000000-0000-0000-0000-000000000009', 'Water Treatment Plant',      'Industrial Zone, River Road 1',     980.0000,  'kW', 0),
    ('a1000000-0000-0000-0000-000000000010', 'Airport Terminal',           'Airport Road 1, East District',    1100.0000,  'kW', 0),
    ('a1000000-0000-0000-0000-000000000011', 'Central Train Station',      'Railway Square 1, Downtown',        540.0000,  'kW', 0),
    ('a1000000-0000-0000-0000-000000000012', 'Municipal Swimming Pool',    'Aquatic Drive 7, West District',    210.0000,  'kW', 0),
    ('a1000000-0000-0000-0000-000000000013', 'City Museum',                'Culture Street 3, Old Town',        130.0000,  'kW', 0),
    ('a1000000-0000-0000-0000-000000000014', 'East District Kindergarten', 'Blossom Lane 9, East District',      45.0000,  'kW', 0),
    ('a1000000-0000-0000-0000-000000000015', 'Central Post Office',        'Postal Square 2, Midtown',           80.0000,  'kW', 0),
    ('a1000000-0000-0000-0000-000000000016', 'Solar Research Center',      'Innovation Park, Tech Road 5',      420.0000,  'kW', 0),
    ('a1000000-0000-0000-0000-000000000017', 'Waste Management Facility',  'Industrial Zone, Loop Road 12',     650.0000,  'kW', 0),
    ('a1000000-0000-0000-0000-000000000018', 'District Courthouse',        'Law Street 1, Downtown',            175.0000,  'kW', 0),
    ('a1000000-0000-0000-0000-000000000019', 'Sports Complex - Outdoor',   'Park Avenue 30, Green District',    290.0000,  'kW', 0),
    ('a1000000-0000-0000-0000-000000000020', 'City Convention Center',     'Congress Boulevard 1, Downtown',    760.0000,  'kW', 0);

-- ============================================================
-- Devices — City Hall (total capacity: 550 kW)
-- ============================================================

INSERT INTO smartcity.public.energy_device (id, building_id, type, rated_capacity_value, rated_capacity_unit, production_value, production_unit) VALUES
    ('d1000000-0001-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000001', 'SOLAR',   200.0000, 'kW', 160.0000, 'kW'),
    ('d1000000-0001-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000001', 'SOLAR',   200.0000, 'kW', 155.0000, 'kW'),
    ('d1000000-0001-0000-0000-000000000003', 'a1000000-0000-0000-0000-000000000001', 'BATTERY', 100.0000, 'kW',  40.0000, 'kW'),
    ('d1000000-0001-0000-0000-000000000004', 'a1000000-0000-0000-0000-000000000001', 'PUMP',     50.0000, 'kW',  30.0000, 'kW');

-- ============================================================
-- Devices — Public Library (total capacity: 230 kW)
-- ============================================================

INSERT INTO smartcity.public.energy_device (id, building_id, type, rated_capacity_value, rated_capacity_unit, production_value, production_unit) VALUES
    ('d1000000-0002-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000002', 'SOLAR',   150.0000, 'kW', 110.0000, 'kW'),
    ('d1000000-0002-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000002', 'BATTERY',  80.0000, 'kW',  25.0000, 'kW');

-- ============================================================
-- Devices — Sports Arena (total capacity: 1500 kW)
-- ============================================================

INSERT INTO smartcity.public.energy_device (id, building_id, type, rated_capacity_value, rated_capacity_unit, production_value, production_unit) VALUES
    ('d1000000-0003-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000003', 'SOLAR',   300.0000, 'kW', 240.0000, 'kW'),
    ('d1000000-0003-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000003', 'SOLAR',   300.0000, 'kW', 235.0000, 'kW'),
    ('d1000000-0003-0000-0000-000000000003', 'a1000000-0000-0000-0000-000000000003', 'SOLAR',   300.0000, 'kW', 220.0000, 'kW'),
    ('d1000000-0003-0000-0000-000000000004', 'a1000000-0000-0000-0000-000000000003', 'BATTERY', 200.0000, 'kW',  80.0000, 'kW'),
    ('d1000000-0003-0000-0000-000000000005', 'a1000000-0000-0000-0000-000000000003', 'BATTERY', 200.0000, 'kW',  75.0000, 'kW'),
    ('d1000000-0003-0000-0000-000000000006', 'a1000000-0000-0000-0000-000000000003', 'PUMP',    100.0000, 'kW',  60.0000, 'kW'),
    ('d1000000-0003-0000-0000-000000000007', 'a1000000-0000-0000-0000-000000000003', 'PUMP',    100.0000, 'kW',  55.0000, 'kW');

-- ============================================================
-- Devices — Central Hospital (total capacity: 1030 kW)
-- ============================================================

INSERT INTO smartcity.public.energy_device (id, building_id, type, rated_capacity_value, rated_capacity_unit, production_value, production_unit) VALUES
    ('d1000000-0004-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000004', 'SOLAR',   250.0000, 'kW', 200.0000, 'kW'),
    ('d1000000-0004-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000004', 'SOLAR',   250.0000, 'kW', 195.0000, 'kW'),
    ('d1000000-0004-0000-0000-000000000003', 'a1000000-0000-0000-0000-000000000004', 'BATTERY', 150.0000, 'kW',  90.0000, 'kW'),
    ('d1000000-0004-0000-0000-000000000004', 'a1000000-0000-0000-0000-000000000004', 'BATTERY', 150.0000, 'kW',  85.0000, 'kW'),
    ('d1000000-0004-0000-0000-000000000005', 'a1000000-0000-0000-0000-000000000004', 'BATTERY', 150.0000, 'kW',  80.0000, 'kW'),
    ('d1000000-0004-0000-0000-000000000006', 'a1000000-0000-0000-0000-000000000004', 'PUMP',     80.0000, 'kW',  50.0000, 'kW');

-- ============================================================
-- Devices — North District School (total capacity: 180 kW)
-- ============================================================

INSERT INTO smartcity.public.energy_device (id, building_id, type, rated_capacity_value, rated_capacity_unit, production_value, production_unit) VALUES
    ('d1000000-0005-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000005', 'SOLAR',   100.0000, 'kW',  70.0000, 'kW'),
    ('d1000000-0005-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000005', 'BATTERY',  50.0000, 'kW',  20.0000, 'kW'),
    ('d1000000-0005-0000-0000-000000000003', 'a1000000-0000-0000-0000-000000000005', 'PUMP',     30.0000, 'kW',  18.0000, 'kW');

-- ============================================================
-- Devices — Police Station (total capacity: 240 kW)
-- ============================================================

INSERT INTO smartcity.public.energy_device (id, building_id, type, rated_capacity_value, rated_capacity_unit, production_value, production_unit) VALUES
    ('d1000000-0006-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000006', 'SOLAR',   120.0000, 'kW',  85.0000, 'kW'),
    ('d1000000-0006-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000006', 'BATTERY',  80.0000, 'kW',  35.0000, 'kW'),
    ('d1000000-0006-0000-0000-000000000003', 'a1000000-0000-0000-0000-000000000006', 'PUMP',     40.0000, 'kW',  25.0000, 'kW');

-- ============================================================
-- Devices — Fire Station No. 2 (total capacity: 210 kW)
-- ============================================================

INSERT INTO smartcity.public.energy_device (id, building_id, type, rated_capacity_value, rated_capacity_unit, production_value, production_unit) VALUES
    ('d1000000-0007-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000007', 'SOLAR',   100.0000, 'kW',  75.0000, 'kW'),
    ('d1000000-0007-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000007', 'BATTERY',  60.0000, 'kW',  28.0000, 'kW'),
    ('d1000000-0007-0000-0000-000000000003', 'a1000000-0000-0000-0000-000000000007', 'PUMP',     50.0000, 'kW',  35.0000, 'kW');

-- ============================================================
-- Devices — Community Center (total capacity: 400 kW)
-- ============================================================

INSERT INTO smartcity.public.energy_device (id, building_id, type, rated_capacity_value, rated_capacity_unit, production_value, production_unit) VALUES
    ('d1000000-0008-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000008', 'SOLAR',   150.0000, 'kW', 115.0000, 'kW'),
    ('d1000000-0008-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000008', 'SOLAR',   150.0000, 'kW', 110.0000, 'kW'),
    ('d1000000-0008-0000-0000-000000000003', 'a1000000-0000-0000-0000-000000000008', 'BATTERY', 100.0000, 'kW',  42.0000, 'kW');

-- ============================================================
-- Devices — Water Treatment Plant (total capacity: 1500 kW)
-- ============================================================

INSERT INTO smartcity.public.energy_device (id, building_id, type, rated_capacity_value, rated_capacity_unit, production_value, production_unit) VALUES
    ('d1000000-0009-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000009', 'SOLAR',   400.0000, 'kW', 320.0000, 'kW'),
    ('d1000000-0009-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000009', 'SOLAR',   400.0000, 'kW', 310.0000, 'kW'),
    ('d1000000-0009-0000-0000-000000000003', 'a1000000-0000-0000-0000-000000000009', 'PUMP',    200.0000, 'kW', 140.0000, 'kW'),
    ('d1000000-0009-0000-0000-000000000004', 'a1000000-0000-0000-0000-000000000009', 'PUMP',    200.0000, 'kW', 135.0000, 'kW'),
    ('d1000000-0009-0000-0000-000000000005', 'a1000000-0000-0000-0000-000000000009', 'BATTERY', 150.0000, 'kW',  70.0000, 'kW'),
    ('d1000000-0009-0000-0000-000000000006', 'a1000000-0000-0000-0000-000000000009', 'BATTERY', 150.0000, 'kW',  65.0000, 'kW');

-- ============================================================
-- Devices — Airport Terminal (total capacity: 1800 kW)
-- ============================================================

INSERT INTO smartcity.public.energy_device (id, building_id, type, rated_capacity_value, rated_capacity_unit, production_value, production_unit) VALUES
    ('d1000000-0010-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000010', 'SOLAR',   300.0000, 'kW', 245.0000, 'kW'),
    ('d1000000-0010-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000010', 'SOLAR',   300.0000, 'kW', 240.0000, 'kW'),
    ('d1000000-0010-0000-0000-000000000003', 'a1000000-0000-0000-0000-000000000010', 'SOLAR',   300.0000, 'kW', 230.0000, 'kW'),
    ('d1000000-0010-0000-0000-000000000004', 'a1000000-0000-0000-0000-000000000010', 'SOLAR',   300.0000, 'kW', 225.0000, 'kW'),
    ('d1000000-0010-0000-0000-000000000005', 'a1000000-0000-0000-0000-000000000010', 'BATTERY', 200.0000, 'kW',  90.0000, 'kW'),
    ('d1000000-0010-0000-0000-000000000006', 'a1000000-0000-0000-0000-000000000010', 'BATTERY', 200.0000, 'kW',  85.0000, 'kW'),
    ('d1000000-0010-0000-0000-000000000007', 'a1000000-0000-0000-0000-000000000010', 'PUMP',    100.0000, 'kW',  65.0000, 'kW'),
    ('d1000000-0010-0000-0000-000000000008', 'a1000000-0000-0000-0000-000000000010', 'PUMP',    100.0000, 'kW',  60.0000, 'kW');

-- ============================================================
-- Devices — Central Train Station (total capacity: 750 kW)
-- ============================================================

INSERT INTO smartcity.public.energy_device (id, building_id, type, rated_capacity_value, rated_capacity_unit, production_value, production_unit) VALUES
    ('d1000000-0011-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000011', 'SOLAR',   250.0000, 'kW', 195.0000, 'kW'),
    ('d1000000-0011-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000011', 'SOLAR',   250.0000, 'kW', 190.0000, 'kW'),
    ('d1000000-0011-0000-0000-000000000003', 'a1000000-0000-0000-0000-000000000011', 'BATTERY', 150.0000, 'kW',  70.0000, 'kW'),
    ('d1000000-0011-0000-0000-000000000004', 'a1000000-0000-0000-0000-000000000011', 'PUMP',    100.0000, 'kW',  60.0000, 'kW');

-- ============================================================
-- Devices — Municipal Swimming Pool (total capacity: 330 kW)
-- ============================================================

INSERT INTO smartcity.public.energy_device (id, building_id, type, rated_capacity_value, rated_capacity_unit, production_value, production_unit) VALUES
    ('d1000000-0012-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000012', 'SOLAR',   150.0000, 'kW', 110.0000, 'kW'),
    ('d1000000-0012-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000012', 'PUMP',    100.0000, 'kW',  75.0000, 'kW'),
    ('d1000000-0012-0000-0000-000000000003', 'a1000000-0000-0000-0000-000000000012', 'BATTERY',  80.0000, 'kW',  32.0000, 'kW');

-- ============================================================
-- Devices — City Museum (total capacity: 230 kW)
-- ============================================================

INSERT INTO smartcity.public.energy_device (id, building_id, type, rated_capacity_value, rated_capacity_unit, production_value, production_unit) VALUES
    ('d1000000-0013-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000013', 'SOLAR',   130.0000, 'kW',  95.0000, 'kW'),
    ('d1000000-0013-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000013', 'BATTERY', 100.0000, 'kW',  45.0000, 'kW');

-- ============================================================
-- Devices — East District Kindergarten (total capacity: 90 kW)
-- ============================================================

INSERT INTO smartcity.public.energy_device (id, building_id, type, rated_capacity_value, rated_capacity_unit, production_value, production_unit) VALUES
    ('d1000000-0014-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000014', 'SOLAR',    60.0000, 'kW',  42.0000, 'kW'),
    ('d1000000-0014-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000014', 'BATTERY',  30.0000, 'kW',  12.0000, 'kW');

-- ============================================================
-- Devices — Central Post Office (total capacity: 150 kW)
-- ============================================================

INSERT INTO smartcity.public.energy_device (id, building_id, type, rated_capacity_value, rated_capacity_unit, production_value, production_unit) VALUES
    ('d1000000-0015-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000015', 'SOLAR',    90.0000, 'kW',  65.0000, 'kW'),
    ('d1000000-0015-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000015', 'BATTERY',  60.0000, 'kW',  28.0000, 'kW');

-- ============================================================
-- Devices — Solar Research Center (total capacity: 600 kW)
-- ============================================================

INSERT INTO smartcity.public.energy_device (id, building_id, type, rated_capacity_value, rated_capacity_unit, production_value, production_unit) VALUES
    ('d1000000-0016-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000016', 'SOLAR',   200.0000, 'kW', 175.0000, 'kW'),
    ('d1000000-0016-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000016', 'SOLAR',   200.0000, 'kW', 170.0000, 'kW'),
    ('d1000000-0016-0000-0000-000000000003', 'a1000000-0000-0000-0000-000000000016', 'SOLAR',   200.0000, 'kW', 165.0000, 'kW');

-- ============================================================
-- Devices — Waste Management Facility (total capacity: 900 kW)
-- ============================================================

INSERT INTO smartcity.public.energy_device (id, building_id, type, rated_capacity_value, rated_capacity_unit, production_value, production_unit) VALUES
    ('d1000000-0017-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000017', 'SOLAR',   300.0000, 'kW', 235.0000, 'kW'),
    ('d1000000-0017-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000017', 'PUMP',    200.0000, 'kW', 150.0000, 'kW'),
    ('d1000000-0017-0000-0000-000000000003', 'a1000000-0000-0000-0000-000000000017', 'PUMP',    200.0000, 'kW', 145.0000, 'kW'),
    ('d1000000-0017-0000-0000-000000000004', 'a1000000-0000-0000-0000-000000000017', 'BATTERY', 200.0000, 'kW',  88.0000, 'kW');

-- ============================================================
-- Devices — District Courthouse (total capacity: 270 kW)
-- ============================================================

INSERT INTO smartcity.public.energy_device (id, building_id, type, rated_capacity_value, rated_capacity_unit, production_value, production_unit) VALUES
    ('d1000000-0018-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000018', 'SOLAR',   150.0000, 'kW', 110.0000, 'kW'),
    ('d1000000-0018-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000018', 'BATTERY',  80.0000, 'kW',  38.0000, 'kW'),
    ('d1000000-0018-0000-0000-000000000003', 'a1000000-0000-0000-0000-000000000018', 'PUMP',     40.0000, 'kW',  25.0000, 'kW');

-- ============================================================
-- Devices — Sports Complex - Outdoor (total capacity: 500 kW)
-- ============================================================

INSERT INTO smartcity.public.energy_device (id, building_id, type, rated_capacity_value, rated_capacity_unit, production_value, production_unit) VALUES
    ('d1000000-0019-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000019', 'SOLAR',   200.0000, 'kW', 155.0000, 'kW'),
    ('d1000000-0019-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000019', 'SOLAR',   200.0000, 'kW', 150.0000, 'kW'),
    ('d1000000-0019-0000-0000-000000000003', 'a1000000-0000-0000-0000-000000000019', 'BATTERY', 100.0000, 'kW',  44.0000, 'kW');

-- ============================================================
-- Devices — City Convention Center (total capacity: 1100 kW)
-- ============================================================

INSERT INTO smartcity.public.energy_device (id, building_id, type, rated_capacity_value, rated_capacity_unit, production_value, production_unit) VALUES
    ('d1000000-0020-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000020', 'SOLAR',   300.0000, 'kW', 240.0000, 'kW'),
    ('d1000000-0020-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000020', 'SOLAR',   300.0000, 'kW', 235.0000, 'kW'),
    ('d1000000-0020-0000-0000-000000000003', 'a1000000-0000-0000-0000-000000000020', 'SOLAR',   300.0000, 'kW', 228.0000, 'kW'),
    ('d1000000-0020-0000-0000-000000000004', 'a1000000-0000-0000-0000-000000000020', 'BATTERY', 100.0000, 'kW',  48.0000, 'kW'),
    ('d1000000-0020-0000-0000-000000000005', 'a1000000-0000-0000-0000-000000000020', 'PUMP',     100.0000, 'kW',  65.0000, 'kW');
