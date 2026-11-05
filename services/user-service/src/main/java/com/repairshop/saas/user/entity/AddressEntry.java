package com.repairshop.saas.user.entity;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One element of the customer_users.addresses jsonb array — the inline
 * replacement for a customer_addresses row. Keeps the original row id (so the
 * "orders store address_id, resolve by id" contract survives the move) plus all
 * structured + legacy-mirrored fields. Plain POJO (not an @Entity); serialized
 * to/from jsonb by CustomerUser.addresses via @JdbcTypeCode(SqlTypes.JSON).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressEntry {
    private UUID id;
    private String label;
    private String fullName;
    private String mobile;
    private String pincode;
    private String locality;
    private String area;
    private String addressLine;
    private String city;
    private String district;
    private String taluk;
    private String state;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Boolean isDefault;
    private Instant createdAt;
    private Instant updatedAt;
}
