-- =============================================================================
-- 28_chat_realtime_signals.sql
--
-- Brings customer<->shop chat to feature-parity with WhatsApp / Message Center:
--   * Snapshots customer + shop display info on the thread so the inbox can
--     render rows (avatar, name, phone) without cross-service joins.
--   * Unread counters + per-side last-read markers (single tick / double tick).
--   * Per-message read receipt + attachment type (text / IMAGE / AUDIO / FILE).
--   * Typing-until timestamps (poll-based "is typing…" indicator — we have no
--     websocket transport; this matches the existing 10s polling pattern).
--   * last_seen_at presence pings on customer_users and shops (drives the
--     green "Online" dot in the chat header).
--
-- Idempotent. Safe to re-run.
-- =============================================================================

BEGIN;

-- ---------- customer_chat_threads --------------------------------------------
ALTER TABLE customer_chat_threads
    ADD COLUMN IF NOT EXISTS customer_name           VARCHAR(120),
    ADD COLUMN IF NOT EXISTS customer_mobile         VARCHAR(20),
    ADD COLUMN IF NOT EXISTS customer_avatar_url     VARCHAR(500),
    ADD COLUMN IF NOT EXISTS shop_name               VARCHAR(160),
    ADD COLUMN IF NOT EXISTS shop_image_url          VARCHAR(500),
    ADD COLUMN IF NOT EXISTS unread_customer_count   INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS unread_shop_count       INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS customer_last_read_at   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS shop_last_read_at       TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS customer_typing_until   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS shop_typing_until       TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_chat_threads_shop_id
    ON customer_chat_threads (shop_id, last_message_at DESC NULLS LAST);

-- ---------- customer_chat_messages -------------------------------------------
ALTER TABLE customer_chat_messages
    ADD COLUMN IF NOT EXISTS read_at         TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS attachment_type VARCHAR(20);  -- IMAGE | AUDIO | FILE

CREATE INDEX IF NOT EXISTS idx_chat_messages_thread_unread
    ON customer_chat_messages (thread_id, sender, read_at);

-- ---------- presence ---------------------------------------------------------
ALTER TABLE customer_users
    ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ;

ALTER TABLE shops
    ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ;

COMMIT;
