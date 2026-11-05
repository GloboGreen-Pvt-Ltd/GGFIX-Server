package com.repairshop.saas.order.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "sell_orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SellOrder {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "sell_number", nullable = false, unique = true, length = 60) private String sellNumber;
    @Column(name = "customer_user_id", nullable = false) private UUID customerUserId;
    @Column(name = "shop_id") private UUID shopId;
    @Column(name = "address_id") private UUID addressId;
    @Column(name = "brand_id") private UUID brandId;
    @Column(name = "model_id") private UUID modelId;
    @Column(name = "ram_option_id") private UUID ramOptionId;
    @Column(name = "storage_option_id") private UUID storageOptionId;
    @Column(length = 100) private String color;
    @Column(length = 50) private String imei;
    @Column(name = "working_condition", length = 50) private String workingCondition;
    @Column(name = "warranty_code", length = 50) private String warrantyCode;
    @Column(name = "device_condition_summary", columnDefinition = "TEXT") private String deviceConditionSummary;
    @Column(name = "payload_json", columnDefinition = "TEXT") private String payloadJson;
    @Column(name = "front_image_url", length = 500) private String frontImageUrl;
    @Column(name = "back_image_url", length = 500) private String backImageUrl;
    @Column(name = "side_image_url", length = 500) private String sideImageUrl;
    @Column(name = "camera_image_url", length = 500) private String cameraImageUrl;
    @Column(name = "other_image_url", length = 500) private String otherImageUrl;
    @Column(nullable = false, length = 50) private String status;
    @Column(name = "final_price", precision = 12, scale = 2) private BigDecimal finalPrice;
    @Column(name = "pickup_date") private LocalDate pickupDate;
    @Column(name = "pickup_slot_start") private LocalTime pickupSlotStart;
    @Column(name = "pickup_slot_end") private LocalTime pickupSlotEnd;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @PrePersist void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null) status = "AWAITING_QUOTATION";
    }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }
}
