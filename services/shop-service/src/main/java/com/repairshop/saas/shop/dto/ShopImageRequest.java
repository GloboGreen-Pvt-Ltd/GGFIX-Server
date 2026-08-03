package com.repairshop.saas.shop.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopImageRequest {

    private String imageUrl;
    private Integer sortOrder;
}
