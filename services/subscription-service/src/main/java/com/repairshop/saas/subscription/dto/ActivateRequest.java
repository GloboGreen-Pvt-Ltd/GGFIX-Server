package com.repairshop.saas.subscription.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Body for POST /subscriptions/activate (BASIC record-only activation). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivateRequest {
    private UUID ownerUserId;
    private Integer shopCount;
}
