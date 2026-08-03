package com.repairshop.saas.masterdata.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "master_ram_options")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MasterRamOption {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "value_gb", nullable = false, unique = true)
    private Integer valueGb;

    @Column(nullable = false, length = 20)
    private String label;
}
