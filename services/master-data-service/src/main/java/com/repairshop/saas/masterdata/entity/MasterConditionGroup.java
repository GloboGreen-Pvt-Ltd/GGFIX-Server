package com.repairshop.saas.masterdata.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "master_condition_groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MasterConditionGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    // Device category this condition group belongs to (Mobile/Laptop/...).
    // Null means it applies to every category (shared/global).
    @Column(name = "device_category_id")
    private UUID deviceCategoryId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 50)
    private String flow;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
