package com.repairshop.saas.shop.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ShopDetailsResponse extends ShopSummaryResponse {

    private String email;
    private String state;
    private String pincode;
    private List<String> services;
    private List<String> images;
    private List<PickupSlotResponse> pickupSlots;
}
