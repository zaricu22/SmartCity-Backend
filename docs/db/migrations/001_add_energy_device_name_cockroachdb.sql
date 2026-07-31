-- Migration: add energy_device.name
-- Target: CockroachDB / PostgreSQL
-- Adds the `name` column introduced in EnergyDeviceJpaEntity (commit bcbeae7)
-- and backfills it for existing rows before enforcing NOT NULL.
--
-- Run against smartcity.public schema. Safe to run once; re-running the
-- ADD COLUMN step will fail with "column already exists" if already applied.

-- ============================================================
-- 1. Add the column with a temporary DEFAULT '' — this bypasses the
--    NOT NULL check for existing rows so the ADD COLUMN doesn't fail.
--    The default is removed again in step 4.
-- ============================================================

ALTER TABLE smartcity.public.energy_device
    ADD COLUMN name TEXT NOT NULL DEFAULT '';

-- ============================================================
-- 2. Backfill known rows (matches docs/db/seed-data.sql device names)
-- ============================================================

UPDATE smartcity.public.energy_device SET name = CASE id
    WHEN 'd1000000-0001-0000-0000-000000000001' THEN 'City Hall Solar Panel 1'
    WHEN 'd1000000-0001-0000-0000-000000000002' THEN 'City Hall Solar Panel 2'
    WHEN 'd1000000-0001-0000-0000-000000000003' THEN 'City Hall Battery Bank'
    WHEN 'd1000000-0001-0000-0000-000000000004' THEN 'City Hall Water Pump'

    WHEN 'd1000000-0002-0000-0000-000000000001' THEN 'Public Library Solar Panel'
    WHEN 'd1000000-0002-0000-0000-000000000002' THEN 'Public Library Battery Bank'

    WHEN 'd1000000-0003-0000-0000-000000000001' THEN 'Sports Arena Solar Panel 1'
    WHEN 'd1000000-0003-0000-0000-000000000002' THEN 'Sports Arena Solar Panel 2'
    WHEN 'd1000000-0003-0000-0000-000000000003' THEN 'Sports Arena Solar Panel 3'
    WHEN 'd1000000-0003-0000-0000-000000000004' THEN 'Sports Arena Battery Bank 1'
    WHEN 'd1000000-0003-0000-0000-000000000005' THEN 'Sports Arena Battery Bank 2'
    WHEN 'd1000000-0003-0000-0000-000000000006' THEN 'Sports Arena Water Pump 1'
    WHEN 'd1000000-0003-0000-0000-000000000007' THEN 'Sports Arena Water Pump 2'

    WHEN 'd1000000-0004-0000-0000-000000000001' THEN 'Central Hospital Solar Panel 1'
    WHEN 'd1000000-0004-0000-0000-000000000002' THEN 'Central Hospital Solar Panel 2'
    WHEN 'd1000000-0004-0000-0000-000000000003' THEN 'Central Hospital Battery Bank 1'
    WHEN 'd1000000-0004-0000-0000-000000000004' THEN 'Central Hospital Battery Bank 2'
    WHEN 'd1000000-0004-0000-0000-000000000005' THEN 'Central Hospital Battery Bank 3'
    WHEN 'd1000000-0004-0000-0000-000000000006' THEN 'Central Hospital Water Pump'

    WHEN 'd1000000-0005-0000-0000-000000000001' THEN 'North District School Solar Panel'
    WHEN 'd1000000-0005-0000-0000-000000000002' THEN 'North District School Battery Bank'
    WHEN 'd1000000-0005-0000-0000-000000000003' THEN 'North District School Water Pump'

    WHEN 'd1000000-0006-0000-0000-000000000001' THEN 'Police Station Solar Panel'
    WHEN 'd1000000-0006-0000-0000-000000000002' THEN 'Police Station Battery Bank'
    WHEN 'd1000000-0006-0000-0000-000000000003' THEN 'Police Station Water Pump'

    WHEN 'd1000000-0007-0000-0000-000000000001' THEN 'Fire Station No. 2 Solar Panel'
    WHEN 'd1000000-0007-0000-0000-000000000002' THEN 'Fire Station No. 2 Battery Bank'
    WHEN 'd1000000-0007-0000-0000-000000000003' THEN 'Fire Station No. 2 Water Pump'

    WHEN 'd1000000-0008-0000-0000-000000000001' THEN 'Community Center Solar Panel 1'
    WHEN 'd1000000-0008-0000-0000-000000000002' THEN 'Community Center Solar Panel 2'
    WHEN 'd1000000-0008-0000-0000-000000000003' THEN 'Community Center Battery Bank'

    WHEN 'd1000000-0009-0000-0000-000000000001' THEN 'Water Treatment Plant Solar Panel 1'
    WHEN 'd1000000-0009-0000-0000-000000000002' THEN 'Water Treatment Plant Solar Panel 2'
    WHEN 'd1000000-0009-0000-0000-000000000003' THEN 'Water Treatment Plant Water Pump 1'
    WHEN 'd1000000-0009-0000-0000-000000000004' THEN 'Water Treatment Plant Water Pump 2'
    WHEN 'd1000000-0009-0000-0000-000000000005' THEN 'Water Treatment Plant Battery Bank 1'
    WHEN 'd1000000-0009-0000-0000-000000000006' THEN 'Water Treatment Plant Battery Bank 2'

    WHEN 'd1000000-0010-0000-0000-000000000001' THEN 'Airport Terminal Solar Panel 1'
    WHEN 'd1000000-0010-0000-0000-000000000002' THEN 'Airport Terminal Solar Panel 2'
    WHEN 'd1000000-0010-0000-0000-000000000003' THEN 'Airport Terminal Solar Panel 3'
    WHEN 'd1000000-0010-0000-0000-000000000004' THEN 'Airport Terminal Solar Panel 4'
    WHEN 'd1000000-0010-0000-0000-000000000005' THEN 'Airport Terminal Battery Bank 1'
    WHEN 'd1000000-0010-0000-0000-000000000006' THEN 'Airport Terminal Battery Bank 2'
    WHEN 'd1000000-0010-0000-0000-000000000007' THEN 'Airport Terminal Water Pump 1'
    WHEN 'd1000000-0010-0000-0000-000000000008' THEN 'Airport Terminal Water Pump 2'

    WHEN 'd1000000-0011-0000-0000-000000000001' THEN 'Central Train Station Solar Panel 1'
    WHEN 'd1000000-0011-0000-0000-000000000002' THEN 'Central Train Station Solar Panel 2'
    WHEN 'd1000000-0011-0000-0000-000000000003' THEN 'Central Train Station Battery Bank'
    WHEN 'd1000000-0011-0000-0000-000000000004' THEN 'Central Train Station Water Pump'

    WHEN 'd1000000-0012-0000-0000-000000000001' THEN 'Municipal Swimming Pool Solar Panel'
    WHEN 'd1000000-0012-0000-0000-000000000002' THEN 'Municipal Swimming Pool Water Pump'
    WHEN 'd1000000-0012-0000-0000-000000000003' THEN 'Municipal Swimming Pool Battery Bank'

    WHEN 'd1000000-0013-0000-0000-000000000001' THEN 'City Museum Solar Panel'
    WHEN 'd1000000-0013-0000-0000-000000000002' THEN 'City Museum Battery Bank'

    WHEN 'd1000000-0014-0000-0000-000000000001' THEN 'East District Kindergarten Solar Panel'
    WHEN 'd1000000-0014-0000-0000-000000000002' THEN 'East District Kindergarten Battery Bank'

    WHEN 'd1000000-0015-0000-0000-000000000001' THEN 'Central Post Office Solar Panel'
    WHEN 'd1000000-0015-0000-0000-000000000002' THEN 'Central Post Office Battery Bank'

    WHEN 'd1000000-0016-0000-0000-000000000001' THEN 'Solar Research Center Solar Panel 1'
    WHEN 'd1000000-0016-0000-0000-000000000002' THEN 'Solar Research Center Solar Panel 2'
    WHEN 'd1000000-0016-0000-0000-000000000003' THEN 'Solar Research Center Solar Panel 3'

    WHEN 'd1000000-0017-0000-0000-000000000001' THEN 'Waste Management Facility Solar Panel'
    WHEN 'd1000000-0017-0000-0000-000000000002' THEN 'Waste Management Facility Water Pump 1'
    WHEN 'd1000000-0017-0000-0000-000000000003' THEN 'Waste Management Facility Water Pump 2'
    WHEN 'd1000000-0017-0000-0000-000000000004' THEN 'Waste Management Facility Battery Bank'

    WHEN 'd1000000-0018-0000-0000-000000000001' THEN 'District Courthouse Solar Panel'
    WHEN 'd1000000-0018-0000-0000-000000000002' THEN 'District Courthouse Battery Bank'
    WHEN 'd1000000-0018-0000-0000-000000000003' THEN 'District Courthouse Water Pump'

    WHEN 'd1000000-0019-0000-0000-000000000001' THEN 'Sports Complex - Outdoor Solar Panel 1'
    WHEN 'd1000000-0019-0000-0000-000000000002' THEN 'Sports Complex - Outdoor Solar Panel 2'
    WHEN 'd1000000-0019-0000-0000-000000000003' THEN 'Sports Complex - Outdoor Battery Bank'

    WHEN 'd1000000-0020-0000-0000-000000000001' THEN 'City Convention Center Solar Panel 1'
    WHEN 'd1000000-0020-0000-0000-000000000002' THEN 'City Convention Center Solar Panel 2'
    WHEN 'd1000000-0020-0000-0000-000000000003' THEN 'City Convention Center Solar Panel 3'
    WHEN 'd1000000-0020-0000-0000-000000000004' THEN 'City Convention Center Battery Bank'
    WHEN 'd1000000-0020-0000-0000-000000000005' THEN 'City Convention Center Water Pump'

    ELSE name
END;

-- ============================================================
-- 3. Fallback for rows the CASE above didn't match, i.e. devices not
--    present in seed-data.sql (created later via the API). These still
--    have name = '' from step 1's default and need a real value before
--    step 4 removes that default.
-- ============================================================

UPDATE smartcity.public.energy_device
    SET name = type || ' ' || SUBSTRING(id::TEXT, 1, 8)
    WHERE name = '';

-- ============================================================
-- 4. Remove the '' default from step 1 now that every row has a real
--    name, so future inserts must supply one explicitly (matches
--    EnergyDeviceJpaEntity's @Column(nullable = false), no default).
-- ============================================================

ALTER TABLE smartcity.public.energy_device
    ALTER COLUMN name DROP DEFAULT;
