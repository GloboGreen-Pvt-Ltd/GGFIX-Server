package com.repairshop.saas.pickup.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PickupOrderEventResponse {
    private UUID id;
    private String status;
    private String note;
    private String actor;
    private Instant createdAt;
}
