package com.repairshop.saas.masterdata.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * A selectable value under a {@link MasterDeviceConfigField} — e.g. for
 * "Device Processor": Intel, AMD, Apple Silicon, Qualcomm, Other.
 */
@Entity
@Table(name = "master_device_config_options", indexes = {
    @Index(name = "idx_device_config_option_field", columnList = "field_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MasterDeviceConfigOption {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "field_id", nullable = false)
    private UUID fieldId;

    // "value" is a reserved word in H2/SQL — map to a safe column name.
    @Column(name = "option_value", nullable = false, length = 255)
    private String value;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
