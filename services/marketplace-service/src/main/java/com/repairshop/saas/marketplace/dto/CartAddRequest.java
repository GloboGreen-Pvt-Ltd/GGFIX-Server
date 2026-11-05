package com.repairshop.saas.marketplace.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartAddRequest {

    @NotNull
    private UUID productId;

    private Integer quantity;
}
