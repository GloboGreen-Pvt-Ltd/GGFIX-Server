package com.repairshop.saas.marketplace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateListingRequest {
    @NotBlank private String sellerType;     // CUSTOMER | SHOP
    @NotNull  private UUID sellerId;
    private UUID shopId;
    private UUID categoryId;
    private UUID brandId;
    private UUID modelId;

    @NotBlank private String productName;
    private String productImage;
    private String condition;
    private String description;

    @NotNull private BigDecimal expectedPrice;

    private BigDecimal latitude;
    private BigDecimal longitude;
    private String address;
    private String city;
    private String state;
    private String pincode;
}
