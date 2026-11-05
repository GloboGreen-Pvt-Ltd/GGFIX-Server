package com.repairshop.saas.masterdata.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "master_faq_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MasterFaqItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_active")
    private Boolean isActive;
}
