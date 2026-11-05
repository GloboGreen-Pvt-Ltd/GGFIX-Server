package com.repairshop.saas.marketplace.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** PATCH /shop/marketplace/products/{id}/status */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatusChangeRequest {
    private String status;
    private Integer quantitySold;
}
