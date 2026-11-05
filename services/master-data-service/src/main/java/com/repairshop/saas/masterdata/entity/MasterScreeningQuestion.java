package com.repairshop.saas.masterdata.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "master_screening_questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MasterScreeningQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Device category this question belongs to (Mobile/Laptop/...). Null means
    // it applies to every category (shared/global).
    @Column(name = "device_category_id")
    private UUID deviceCategoryId;

    @Column(nullable = false, length = 50)
    private String flow;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(name = "helper_text", columnDefinition = "TEXT")
    private String helperText;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_active")
    private Boolean isActive;
}
