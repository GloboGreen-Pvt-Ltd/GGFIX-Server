package com.repairshop.saas.ticket.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DatabaseSchemaInitializer {

    private final JdbcTemplate jdbc;

    @Bean
    ApplicationRunner ensureTicketSchema() {
        return args -> {
            addColumnIfMissing("repair_bookings", "assigned_pickup_person_id", "UUID");
            addColumnIfMissing("repair_bookings", "pickup_person_name", "VARCHAR(120)");
            addColumnIfMissing("repair_bookings", "pickup_person_phone", "VARCHAR(30)");
            // Mirrors the order-service initializer — ticket-service can boot
            // first in some setups, and the pickup-person estimate flow reads
            // this column. Idempotent ADD COLUMN IF NOT EXISTS is safe.
            addColumnIfMissing("repair_booking_services", "warranty", "VARCHAR(20)");
            // Boot-time safety net for migration 48 (tickets.customer_address).
            // The owner Booking Details "Customer Details" card reads this
            // column; without it the JPA save fails on every ticket mint.
            addColumnIfMissing("tickets", "customer_address", "TEXT");
            // Booking drafts (BookingDraftController) live in their own table,
            // accessed through JdbcTemplate only — no JPA entity, so a missing
            // table can never fail `ddl-auto: validate`. Mirrors
            // postgres/init/104_booking_drafts.sql.
            ensureBookingDraftsTable(jdbc);
        };
    }

    /**
     * DDL for booking_drafts. Portable across Postgres and the H2 dev profile:
     * payload / ticket_ids are JSON stored as TEXT (H2 has no jsonb).
     */
    public static final String BOOKING_DRAFTS_TABLE_DDL =
            "CREATE TABLE IF NOT EXISTS booking_drafts ("
            + " id UUID PRIMARY KEY,"
            + " shop_id UUID NOT NULL,"
            + " created_by UUID NOT NULL,"
            + " updated_by UUID,"
            + " status VARCHAR(20) NOT NULL,"
            + " current_step VARCHAR(60),"
            + " step_label VARCHAR(80),"
            + " customer_name VARCHAR(160),"
            + " customer_mobile VARCHAR(30),"
            + " device_summary VARCHAR(255),"
            + " device_count INT NOT NULL DEFAULT 0,"
            + " payload TEXT NOT NULL,"
            + " version INT NOT NULL,"
            + " ticket_ids TEXT,"
            + " created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,"
            + " updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,"
            + " submitted_at TIMESTAMP WITH TIME ZONE,"
            + " CONSTRAINT chk_booking_drafts_status CHECK (status IN ('ACTIVE', 'SUBMITTED', 'DISCARDED'))"
            + ")";

    public static final String BOOKING_DRAFTS_INDEX_DDL =
            "CREATE INDEX IF NOT EXISTS idx_booking_drafts_shop_status_updated"
            + " ON booking_drafts (shop_id, status, updated_at DESC)";

    /** Idempotent; logs and returns false instead of throwing (e.g. no CREATE privilege). */
    public static boolean ensureBookingDraftsTable(JdbcTemplate jdbc) {
        try {
            jdbc.execute(BOOKING_DRAFTS_TABLE_DDL);
            jdbc.execute(BOOKING_DRAFTS_INDEX_DDL);
            return true;
        } catch (Exception e) {
            log.warn("Could not ensure booking_drafts table exists: {}", e.getMessage());
            return false;
        }
    }

    private void addColumnIfMissing(String tableName, String columnName, String typeSql) {
        try {
            jdbc.execute("ALTER TABLE " + tableName + " ADD COLUMN IF NOT EXISTS " + columnName + " " + typeSql);
        } catch (Exception e) {
            log.warn("Could not ensure {}.{} exists: {}", tableName, columnName, e.getMessage());
        }
    }
}
