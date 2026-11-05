package com.repairshop.saas.subscription.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Price quote for BASIC. Single shop = flat 3000; 2+ shops = 2500/shop
 * (discountApplied=true).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuoteResponse {
    private int shopCount;
    private BigDecimal pricePerShop;
    private BigDecimal total;
    private boolean discountApplied;
}
