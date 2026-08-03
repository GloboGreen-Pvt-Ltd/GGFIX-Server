package com.repairshop.saas.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomerOrderResponse {
    private UUID id;
    private String orderNumber;
    private UUID shopId;
    private String orderType;
    private UUID referenceId;
    private String status;
    // Live timeline status for repair-flow orders (REPAIR / PICKUP / ENQUIRY).
    // phaseStatus is the raw event key (e.g. "TECHNICIAN_WORK_STARTED") that
    // matches the SHOP_BOOKING_STATUS_OPTIONS list rendered on the mobile
    // History screen; phaseLabel is the display string the My Orders card
    // shows in place of the macro status "PENDING".
    private String phaseStatus;
    private String phaseLabel;
    private BigDecimal totalAmount;
    private Map<String, Object> payload;
    private Instant createdAt;
    private Instant updatedAt;
}
