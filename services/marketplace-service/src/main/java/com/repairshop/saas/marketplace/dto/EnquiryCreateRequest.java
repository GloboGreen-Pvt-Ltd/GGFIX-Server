package com.repairshop.saas.marketplace.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** POST /customer/marketplace/products/{id}/enquiries */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnquiryCreateRequest {
    private String kind; // ENQUIRY | PURCHASE_REQUEST
    private String message;
    private BigDecimal offerAmount;
    private Integer quantity;
}
