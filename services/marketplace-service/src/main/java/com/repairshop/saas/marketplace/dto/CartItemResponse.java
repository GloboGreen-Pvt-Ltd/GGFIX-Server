package com.repairshop.saas.marketplace.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CartItemResponse {
    private UUID id;
    private UUID productId;
    private Integer quantity;
    private ProductResponse product;
}
