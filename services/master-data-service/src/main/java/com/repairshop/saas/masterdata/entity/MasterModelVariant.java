package com.repairshop.saas.masterdata.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "master_model_variants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MasterModelVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "model_id", nullable = false)
    private UUID modelId;

    @Column(name = "ram_option_id")
    private UUID ramOptionId;

    @Column(name = "storage_option_id")
    private UUID storageOptionId;

    @Column(name = "color_id")
    private UUID colorId;

    @Column(name = "reference_price", precision = 12, scale = 2)
    private BigDecimal referencePrice;
}
