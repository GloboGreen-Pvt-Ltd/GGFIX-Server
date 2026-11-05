package com.repairshop.saas.order.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Cross-service view of ticket-service's tickets table. order-service writes
 * {@code customer_approval} when the customer approves a repair, and reads the
 * shop-entered service details (photos, security, parts, approval, schedule,
 * estimate) so the customer's View Details screen can fall back to the ticket
 * row when the mirror in ticket-service hasn't yet populated the booking row.
 */
@Entity
@Table(name = "tickets")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PlatformTicket {
    @Id private UUID id;
    @Column(name = "customer_approval") private Boolean customerApproval;
    @Column(name = "device_photos_json", columnDefinition = "TEXT", insertable = false, updatable = false) private String devicePhotosJson;
    @Column(name = "device_security_type", length = 20, insertable = false, updatable = false) private String deviceSecurityType;
    @Column(name = "device_security_value", length = 255, insertable = false, updatable = false) private String deviceSecurityValue;
    @Column(name = "missing_parts_json", columnDefinition = "TEXT", insertable = false, updatable = false) private String missingPartsJson;
    @Column(name = "estimated_ready_at", insertable = false, updatable = false) private Instant estimatedReadyAt;
    @Column(name = "estimated_delivery_at", insertable = false, updatable = false) private Instant estimatedDeliveryAt;
    @Column(name = "estimated_price", precision = 12, scale = 2, insertable = false, updatable = false) private BigDecimal estimatedPrice;
    @Column(name = "issue_description", columnDefinition = "TEXT", insertable = false, updatable = false) private String issueDescription;
}
