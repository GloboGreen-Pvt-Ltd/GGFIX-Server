package com.repairshop.saas.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * POST /tickets/{id}/price-revision — change the price of, or cancel, services
 * ALREADY on the ticket. Never adds a service (that is Re-Estimate) and never
 * changes the repair status.
 */
@Data
@Schema(description = "Price Revised: new prices / cancellations for existing line items")
public class PriceRevisionRequest {

    @Schema(description = "One entry per changed line item")
    private List<Change> changes;

    @Schema(description = "Price Correction | Customer Discount | Spare Part Price Change | Service Cancelled | Other")
    private String reason;

    @Schema(description = "Optional notes (required for Other)")
    private String notes;

    @Schema(description = "true = the customer approved the revised estimate at the counter")
    private Boolean customerApproved;

    @Data
    public static class Change {
        @Schema(description = "Position of the item in priceItemsJson as loaded")
        private Integer index;
        @Schema(description = "Item identity as loaded (id, else code, else label) — guards against a concurrent edit")
        private String identity;
        @Schema(description = "Price as loaded — guards against a concurrent edit")
        private BigDecimal oldAmount;
        @Schema(description = "New price (0 or more); ignored when cancel is true")
        private BigDecimal newAmount;
        @Schema(description = "Cancel (remove) this service")
        private Boolean cancel;
    }
}
