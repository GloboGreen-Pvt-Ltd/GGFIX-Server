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
