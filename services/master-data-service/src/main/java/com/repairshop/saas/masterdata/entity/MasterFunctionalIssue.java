package com.repairshop.saas.masterdata.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "master_functional_issues")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MasterFunctionalIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    // Device category this issue belongs to (Mobile/Laptop/...). Null means it
    // applies to every category (shared/global).
    @Column(name = "device_category_id")
    private UUID deviceCategoryId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "icon_url", length = 500)
    private String iconUrl;

    @Column(name = "icon_base64", columnDefinition = "TEXT")
    private String iconBase64;

    @Column(name = "price_impact", precision = 12, scale = 2)
    private BigDecimal priceImpact;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_active")
    private Boolean isActive;
}
