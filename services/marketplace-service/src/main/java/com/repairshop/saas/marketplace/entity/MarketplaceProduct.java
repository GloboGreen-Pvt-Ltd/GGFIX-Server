package com.repairshop.saas.marketplace.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "marketplace_products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketplaceProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "shop_id")
    private UUID shopId;

    @Column(name = "seller_user_id")
    private UUID sellerUserId;

    @Column(name = "brand_id")
    private UUID brandId;

    @Column(name = "model_id")
    private UUID modelId;

    @Column(name = "ram_option_id")
    private UUID ramOptionId;

    @Column(name = "storage_option_id")
    private UUID storageOptionId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 50)
    private String type; // SELL | BUY

    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 50)
    private String status; // DRAFT | ACTIVE | SOLD | INACTIVE

    @Column(name = "condition_label", length = 50)
    private String conditionLabel;

    @Column(length = 100)
    private String color;

    @Column(name = "ram_label", length = 50)
    private String ramLabel;

    @Column(name = "storage_label", length = 50)
    private String storageLabel;

    @Column(length = 50)
    private String network;

    @Column(length = 50)
    private String imei;

    @Column(name = "working_condition", length = 30)
    private String workingCondition;  // WORKING | DEAD | UNKNOWN

    @Column(name = "description_type", length = 30)
    private String descriptionType;  // DETAILED | SHORT | DEAD_SHORT

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /** JSON / comma-separated list of additional image URLs */
    @Column(name = "extra_image_urls", columnDefinition = "TEXT")
    private String extraImageUrls;

    /** JSON blob with the full assessment data (screening answers, conditions, issues, accessories, warranty). */
    @Column(name = "assessment_json", columnDefinition = "TEXT")
    private String assessmentJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null) status = "ACTIVE";
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
