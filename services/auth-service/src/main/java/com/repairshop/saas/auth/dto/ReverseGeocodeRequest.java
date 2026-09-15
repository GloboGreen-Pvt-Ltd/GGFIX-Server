package com.repairshop.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Coordinates to reverse-geocode")
public class ReverseGeocodeRequest {

    @NotNull(message = "latitude is required")
    @DecimalMin(value = "-90", message = "latitude must be >= -90")
    @DecimalMax(value = "90", message = "latitude must be <= 90")
    @Schema(description = "Latitude", required = true)
    private BigDecimal latitude;

    @NotNull(message = "longitude is required")
    @DecimalMin(value = "-180", message = "longitude must be >= -180")
    @DecimalMax(value = "180", message = "longitude must be <= 180")
    @Schema(description = "Longitude", required = true)
    private BigDecimal longitude;
}
