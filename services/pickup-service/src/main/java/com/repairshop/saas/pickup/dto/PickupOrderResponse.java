package com.repairshop.saas.pickup.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PickupOrderResponse {
    private UUID id;
    private String orderNumber;
    private UUID customerUserId;
    private UUID shopId;
    private UUID ticketId;
    private UUID addressId;
    private String flowType;
    private LocalDate pickupDate;
    private LocalTime pickupSlotStart;
    private LocalTime pickupSlotEnd;
    private String status;
    private BigDecimal estimateAmount;
    private BigDecimal finalAmount;
    private String note;
    private Instant createdAt;
    private List<PickupOrderEventResponse> events;
}
