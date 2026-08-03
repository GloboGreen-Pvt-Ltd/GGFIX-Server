package com.repairshop.saas.masterdata.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "master_condition_options")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MasterConditionOption {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(nullable = false, length = 255)
    private String label;

    @Column(name = "icon_url", length = 500)
    private String iconUrl;

    @Column(name = "icon_base64", columnDefinition = "TEXT")
    private String iconBase64;

    @Column(name = "price_impact", precision = 12, scale = 2)
    private BigDecimal priceImpact;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
