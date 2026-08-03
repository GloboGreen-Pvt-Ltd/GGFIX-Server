package com.repairshop.saas.ticket.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "repair_booking_services")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PlatformRepairBookingService {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "booking_id", nullable = false) private UUID bookingId;
    @Column(name = "repair_service_id") private UUID repairServiceId;
    @Column(name = "service_code", length = 50) private String serviceCode;
    @Column(name = "service_name", length = 255) private String serviceName;
    @Column(name = "estimated_price", precision = 12, scale = 2) private BigDecimal estimatedPrice;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    @PrePersist void prePersist() { if (createdAt == null) createdAt = Instant.now(); }
}
