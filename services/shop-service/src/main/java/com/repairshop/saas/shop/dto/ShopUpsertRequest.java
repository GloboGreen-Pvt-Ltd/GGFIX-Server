package com.repairshop.saas.shop.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopUpsertRequest {

    private String name;
    private String slug;
    private String email;
    private String address;
    private String timezone;

    // Boolean fields named "isXxx" interact badly with Jackson's bean
    // introspection — it can silently strip the "is" prefix and look for an
    // "active" property instead of "isActive", so a PUT with `{"isActive":true}`
    // ends up with this field null. The annotations make both names bind.
    @JsonProperty("isActive")
    @JsonAlias({"active"})
    private Boolean isActive;

    private BigDecimal latitude;
    private BigDecimal longitude;
    private String city;
    private String state;
    private String pincode;
    private BigDecimal rating;
}
