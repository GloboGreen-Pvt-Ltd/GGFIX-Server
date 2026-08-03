package com.repairshop.saas.pickup.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RescheduleRequest {
    private LocalDate pickupDate;
    private LocalTime pickupSlotStart;
    private LocalTime pickupSlotEnd;
}
