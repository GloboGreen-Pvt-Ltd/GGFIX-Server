package com.repairshop.saas.shop.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ShopSummaryResponse {

    private UUID id;
    private String name;
    private String slug;
    private String city;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal rating;
    private Double distanceKm;
    private Boolean isOpen;
}
