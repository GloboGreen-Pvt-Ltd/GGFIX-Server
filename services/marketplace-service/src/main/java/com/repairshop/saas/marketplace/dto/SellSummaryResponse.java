package com.repairshop.saas.marketplace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SellSummaryResponse {
    private long active;
    private long draft;
    private long sold;
    private long inactive;
    private long enquiriesOpen;
    private long purchaseRequestsOpen;
    private long enquiriesUnread;
}
