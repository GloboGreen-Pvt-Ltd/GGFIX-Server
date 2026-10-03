-- =============================================================================
-- 98_device_category_specs.sql
--
-- Category-specific device specifications for repair bookings (tickets) and
-- sell listings (marketplace_products).
--
-- WHY
-- The Partner App's "Your Device" screens (Booking and Sell) asked every
-- category for the Mobile/Tablet pair of master RAM + Storage options. That
-- describes a phone, not a laptop (which also needs a storage TYPE), a
-- smartwatch (case size, connectivity) or a pair of earbuds (device type,
-- connectivity). The apps now collect the right attributes per category, and
-- they need somewhere to land as separate, filterable values — not folded into
-- device_display_name as "16GB / 512GB NVMe SSD".
--
-- WHY NOT REUSE ram_option_id / storage_option_id
-- Those are FKs into master_ram_options / master_storage_options, a phone-sized
-- list with no storage type, and they stay exactly as they are for MOBILE and
-- TABLET. Laptop / Smartwatch / Audio values are stored in the columns below.
--
-- THE COLUMNS (same on both tables)
--   device_category   MOBILE | TABLET | LAPTOP | SMARTWATCH | AUDIO_DEVICE
--   ram               LAPTOP          e.g. 16GB
--   storage_capacity  LAPTOP          e.g. 512GB, 1TB
--   storage_type      LAPTOP          HDD | SATA_SSD | NVME_SSD
--   case_size         SMARTWATCH      e.g. 44MM
--   connectivity      SMARTWATCH / AUDIO_DEVICE   e.g. GPS_CELLULAR, BLUETOOTH
--   device_type       AUDIO_DEVICE    e.g. TWS_EARBUDS, BLUETOOTH_SPEAKER
-- Values are normalized by common-device-specs before the write (ticket-service
-- and marketplace-service share it), so "16 GB", "16gb" and "16GB" are one RAM
-- size, and the UI turns "NVME_SSD" back into "NVMe SSD".
--
-- BACKWARD COMPATIBLE
-- Additive only: every column is NULLable, nothing is renamed, dropped or
-- backfilled. Existing rows — and every MOBILE / TABLET row going forward —
-- keep NULLs here. Older app builds that don't send deviceCategory are accepted
-- and leave these columns untouched.
--
-- DEPLOY ORDER
-- ticket-service and marketplace-service run with ddl-auto=validate and map
-- these columns, so apply this file to the database BEFORE deploying them.
-- Idempotent — safe to re-run.
--
-- marketplace_products is altered only when it exists: migration 68 drops it on
-- a database built from scratch, while the live marketplace-service still maps
-- it (Sell → "Sell Your Device" listings are written there).
-- =============================================================================

ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS device_category  VARCHAR(30),
    ADD COLUMN IF NOT EXISTS ram              VARCHAR(20),
    ADD COLUMN IF NOT EXISTS storage_capacity VARCHAR(20),
    ADD COLUMN IF NOT EXISTS storage_type     VARCHAR(20),
    ADD COLUMN IF NOT EXISTS case_size        VARCHAR(20),
    ADD COLUMN IF NOT EXISTS connectivity     VARCHAR(40),
    ADD COLUMN IF NOT EXISTS device_type      VARCHAR(40);

-- The two closed sets. Values are normalized before the write, so a row that
-- violates either constraint is a bug on the way in rather than data to
-- tolerate. connectivity / device_type are deliberately open (admins can add
-- values in master data), so they get no list here.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_tickets_device_category') THEN
        ALTER TABLE tickets
            ADD CONSTRAINT chk_tickets_device_category
            CHECK (device_category IS NULL
                   OR device_category IN ('MOBILE', 'TABLET', 'LAPTOP', 'SMARTWATCH', 'AUDIO_DEVICE'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_tickets_storage_type') THEN
        ALTER TABLE tickets
            ADD CONSTRAINT chk_tickets_storage_type
            CHECK (storage_type IS NULL OR storage_type IN ('HDD', 'SATA_SSD', 'NVME_SSD'));
    END IF;
END $$;

-- For "all NVMe laptops in for repair" style filters and reports.
CREATE INDEX IF NOT EXISTS idx_tickets_device_category ON tickets (device_category);

COMMENT ON COLUMN tickets.device_category IS
    'Which spec schema the device follows: MOBILE | TABLET | LAPTOP | SMARTWATCH | AUDIO_DEVICE. MOBILE/TABLET use ram_option_id/storage_option_id; the others use the columns below. NULL on tickets booked before migration 98.';
COMMENT ON COLUMN tickets.ram IS 'LAPTOP RAM, normalized (e.g. 16GB).';
COMMENT ON COLUMN tickets.storage_capacity IS 'LAPTOP storage capacity, normalized (e.g. 512GB, 1TB).';
COMMENT ON COLUMN tickets.storage_type IS 'LAPTOP storage type: HDD | SATA_SSD | NVME_SSD.';
COMMENT ON COLUMN tickets.case_size IS 'SMARTWATCH case size, normalized (e.g. 44MM).';
COMMENT ON COLUMN tickets.connectivity IS 'SMARTWATCH / AUDIO_DEVICE connectivity code (e.g. GPS_CELLULAR, BLUETOOTH).';
COMMENT ON COLUMN tickets.device_type IS 'AUDIO_DEVICE type code (e.g. TWS_EARBUDS, BLUETOOTH_SPEAKER).';

-- Same columns, constraints and meaning on sell listings.
DO $$
BEGIN
    IF to_regclass('public.marketplace_products') IS NULL THEN
        RAISE NOTICE '98: marketplace_products not present, skipping its columns';
        RETURN;
    END IF;

    ALTER TABLE marketplace_products
        ADD COLUMN IF NOT EXISTS device_category  VARCHAR(30),
        ADD COLUMN IF NOT EXISTS ram              VARCHAR(20),
        ADD COLUMN IF NOT EXISTS storage_capacity VARCHAR(20),
        ADD COLUMN IF NOT EXISTS storage_type     VARCHAR(20),
        ADD COLUMN IF NOT EXISTS case_size        VARCHAR(20),
        ADD COLUMN IF NOT EXISTS connectivity     VARCHAR(40),
        ADD COLUMN IF NOT EXISTS device_type      VARCHAR(40);

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_marketplace_products_device_category') THEN
        ALTER TABLE marketplace_products
            ADD CONSTRAINT chk_marketplace_products_device_category
            CHECK (device_category IS NULL
                   OR device_category IN ('MOBILE', 'TABLET', 'LAPTOP', 'SMARTWATCH', 'AUDIO_DEVICE'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_marketplace_products_storage_type') THEN
        ALTER TABLE marketplace_products
            ADD CONSTRAINT chk_marketplace_products_storage_type
            CHECK (storage_type IS NULL OR storage_type IN ('HDD', 'SATA_SSD', 'NVME_SSD'));
    END IF;

    CREATE INDEX IF NOT EXISTS idx_marketplace_products_device_category ON marketplace_products (device_category);

    COMMENT ON COLUMN marketplace_products.device_category IS
        'Which spec schema the device follows: MOBILE | TABLET | LAPTOP | SMARTWATCH | AUDIO_DEVICE. Same meaning as tickets.device_category.';
    COMMENT ON COLUMN marketplace_products.ram IS 'LAPTOP RAM, normalized (e.g. 16GB).';
    COMMENT ON COLUMN marketplace_products.storage_capacity IS 'LAPTOP storage capacity, normalized (e.g. 512GB, 1TB).';
    COMMENT ON COLUMN marketplace_products.storage_type IS 'LAPTOP storage type: HDD | SATA_SSD | NVME_SSD.';
    COMMENT ON COLUMN marketplace_products.case_size IS 'SMARTWATCH case size, normalized (e.g. 44MM).';
    COMMENT ON COLUMN marketplace_products.connectivity IS 'SMARTWATCH / AUDIO_DEVICE connectivity code (e.g. GPS_CELLULAR, BLUETOOTH).';
    COMMENT ON COLUMN marketplace_products.device_type IS 'AUDIO_DEVICE type code (e.g. TWS_EARBUDS, BLUETOOTH_SPEAKER).';
END $$;
