package com.repairshop.saas.marketplace.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartUpdateRequest {

    @NotNull
    @Min(1)
    private Integer quantity;
}
