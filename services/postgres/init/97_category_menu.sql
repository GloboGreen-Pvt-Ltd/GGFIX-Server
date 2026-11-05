-- =============================================================================
-- 97_category_menu.sql
--
-- Backs Admin panel -> Master Data -> Category Menu Management.
--
-- One row per tile shown on the customer-facing site's Repair / Sell / Buy
-- category menus: the tile's name, image and where it sorts. Today those tiles
-- are hardcoded PNGs per screen (see SELL_IMAGES in the customer app); this
-- table lets the admin configure name + image instead of shipping an app
-- update for every tile change.
--
-- category_type is a CHECK constraint rather than its own lookup table — only
-- three values exist (REPAIR / SELL / BUY) and they map 1:1 to the site's three
-- menus, so a join table would only add a round trip with no query that needs
-- it. Same call already made for master_banners / master_faq_items, which have
-- no type column at all because they don't need one; this table's closest
-- sibling for the "few fixed values, indexed, filtered on every read" shape is
-- ticket-service's status columns.
--
-- image_url holds the public media.ggfix.in URL, exactly as master_banners.image_url
-- and model_compatibility.reference_image_url do. No image_key or content-type
-- columns: TaxonomyMediaService documents why that metadata was dropped after
-- the same idea drifted the live schema twice.
--
-- Idempotent — IF NOT EXISTS throughout, so re-running is a no-op.
-- =============================================================================

CREATE TABLE IF NOT EXISTS master_category_menu (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_type   VARCHAR(20)  NOT NULL,
    menu_name       VARCHAR(255) NOT NULL,
    slug            VARCHAR(255),
    description     TEXT,
    image_url       TEXT,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_master_category_menu_category_type
        CHECK (category_type IN ('REPAIR', 'SELL', 'BUY'))
);

-- The admin filters this list by category_type constantly (one tab per menu),
-- so that lookup should not need a sequential scan as the table grows.
CREATE INDEX IF NOT EXISTS ix_master_category_menu_category_type
    ON master_category_menu (category_type);
