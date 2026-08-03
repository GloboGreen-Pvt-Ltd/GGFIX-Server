package com.repairshop.saas.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_saved_devices", indexes = {
    @Index(name = "idx_customer_saved_devices_user", columnList = "customer_user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerSavedDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_user_id", nullable = false)
    private UUID customerUserId;

    @Column(name = "category_id")
    private UUID categoryId;

    // Category code (SMARTPHONE | LAPTOP | SMARTWATCH | TABLET | AUDIO | SPEAKER).
    // Stored alongside the UUID because the Home tiles deep-link the wizard
    // with a hard-coded string code (no UUID known), and the repair flow
    // filters saved devices by this code per category.
    @Column(name = "category_code", length = 50)
    private String categoryCode;

    @Column(name = "brand_id")
    private UUID brandId;

    @Column(name = "model_id")
    private UUID modelId;

    // Denormalized display fields captured at save time so the app can render
    // the device (name, brand, RAM/storage) without joining master-data, which
    // lives in another service.
    @Column(name = "model_name", length = 255)
    private String modelName;

    @Column(name = "brand_name", length = 150)
    private String brandName;

    @Column(name = "ram_label", length = 50)
    private String ramLabel;

    @Column(name = "storage_label", length = 50)
    private String storageLabel;

    @Column(name = "ram_option_id")
    private UUID ramOptionId;

    @Column(name = "storage_option_id")
    private UUID storageOptionId;

    @Column(name = "color", length = 100)
    private String color;

    @Column(name = "imei", length = 50)
    private String imei;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "is_default")
    private Boolean isDefault;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (isDefault == null) isDefault = false;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
