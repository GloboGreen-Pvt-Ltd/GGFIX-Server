package com.repairshop.saas.masterdata.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(
        name = "master_category_brand_mapping",
        uniqueConstraints = @UniqueConstraint(name = "uq_category_brand", columnNames = { "category_id", "brand_id" })
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MasterCategoryBrandMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "brand_id", nullable = false)
    private UUID brandId;
}
