package com.repairshop.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Create a new shop")
public class CreateShopRequest {

    @NotBlank(message = "Name is required")
    @Schema(description = "Shop name", required = true)
    private String name;

    @NotBlank(message = "Slug is required")
    @Schema(description = "Unique slug (URL id)", required = true)
    private String slug;

    @Schema(description = "Address (optional)")
    private String address;
}
