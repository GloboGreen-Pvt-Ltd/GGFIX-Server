package com.repairshop.saas.shop.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopServiceRequest {

    private String serviceCode;
    private Boolean isEnabled;
}
