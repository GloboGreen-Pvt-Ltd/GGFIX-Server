package com.repairshop.saas.shop.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PickupSlotResponse {

    private UUID id;
    private Short dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer capacity;
}
