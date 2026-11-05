package com.repairshop.saas.masterdata.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "master_colors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MasterColor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "hex_code", length = 20)
    private String hexCode;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
