package com.repairshop.saas.shop.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shop_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (sortOrder == null) sortOrder = 0;
    }
}
