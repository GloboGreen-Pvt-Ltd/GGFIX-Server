package com.repairshop.saas.marketplace.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnquiryRejectRequest {
    private String reason;
}
