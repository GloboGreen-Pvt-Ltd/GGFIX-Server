package com.repairshop.saas.order.config;

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
    ApplicationRunner ensureOrderSchema() {
        return args -> {
            addColumnIfMissing("repair_bookings", "assigned_pickup_person_id", "UUID");
            addColumnIfMissing("repair_bookings", "pickup_person_name", "VARCHAR(120)");
            addColumnIfMissing("repair_bookings", "pickup_person_phone", "VARCHAR(30)");
            // Pickup-person Repair Estimate flow stores per-service warranty
            // (3M/6M/12M) so the customer and owner can audit what the
            // technician committed to.
            addColumnIfMissing("repair_booking_services", "warranty", "VARCHAR(20)");
            // Customer Sell Leads quotes: listing → sell order link (migration 107).
            addColumnIfMissing("marketplace_listings", "sell_order_id", "UUID");
            // ShopNotification maps it (migration 86).
            addColumnIfMissing("shop_notifications", "ticket_id", "UUID");
            // Partner purchase workflow on sell orders (migration 110).
            String[][] sell = {
                    {"assigned_pickup_person_id", "UUID"}, {"pickup_person_name", "VARCHAR(120)"}, {"pickup_person_phone", "VARCHAR(30)"},
                    {"pickup_assigned_at", "TIMESTAMPTZ"}, {"pickup_accepted_at", "TIMESTAMPTZ"}, {"pickup_started_at", "TIMESTAMPTZ"},
                    {"picked_up_at", "TIMESTAMPTZ"}, {"received_at", "TIMESTAMPTZ"}, {"pickup_issue", "TEXT"},
                    {"inspection_json", "TEXT"}, {"inspected_at", "TIMESTAMPTZ"},
                    {"revised_price", "NUMERIC(12, 2)"}, {"revision_reason", "TEXT"}, {"revision_status", "VARCHAR(20)"}, {"revision_note", "TEXT"},
                    {"payout_method", "VARCHAR(20)"}, {"payout_account", "VARCHAR(120)"}, {"payout_reference", "VARCHAR(80)"},
                    {"payout_amount", "NUMERIC(12, 2)"}, {"payout_recorded_at", "TIMESTAMPTZ"}, {"payout_confirmed_at", "TIMESTAMPTZ"},
                    {"payout_dispute_note", "TEXT"}, {"completed_at", "TIMESTAMPTZ"}, {"cancel_reason", "TEXT"}, {"cancelled_by", "VARCHAR(20)"},
            };
            for (String[] c : sell) addColumnIfMissing("sell_orders", c[0], c[1]);
        };
    }

    private void addColumnIfMissing(String tableName, String columnName, String typeSql) {
        try {
            jdbc.execute("ALTER TABLE " + tableName + " ADD COLUMN IF NOT EXISTS " + columnName + " " + typeSql);
        } catch (Exception e) {
            log.warn("Could not ensure {}.{} exists: {}", tableName, columnName, e.getMessage());
        }
    }
}
