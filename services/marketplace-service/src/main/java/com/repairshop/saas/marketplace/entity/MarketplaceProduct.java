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

    // ---- Sell module (migration 106_marketplace_sell_module.sql) ----

    @Column(name = "listing_type", length = 20)
    private String listingType;  // DEVICE | SPARE_PART | ACCESSORY

    @Column(name = "item_condition", length = 20)
    private String itemCondition;  // NEW | USED | REFURBISHED

    /** Remaining stock. */
    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "sold_quantity", nullable = false)
    private Integer soldQuantity;

    @Column(nullable = false)
    private Boolean negotiable;

    /** Comma-separated PICKUP,SHIPPING. */
    @Column(name = "fulfilment_options", length = 100)
    private String fulfilmentOptions;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    /** Idempotency key, unique per (shop_id, client_ref). */
    @Column(name = "client_ref", length = 64)
    private String clientRef;

    @Column(name = "batch_id")
    private UUID batchId;

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
        if (quantity == null) quantity = 1;
        if (soldQuantity == null) soldQuantity = 0;
        if (negotiable == null) negotiable = false;
        if (fulfilmentOptions == null) fulfilmentOptions = "PICKUP";
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
