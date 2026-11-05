package com.repairshop.saas.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AddressResponse {

    private UUID id;
    private UUID customerUserId;
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
