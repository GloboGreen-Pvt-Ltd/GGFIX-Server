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
