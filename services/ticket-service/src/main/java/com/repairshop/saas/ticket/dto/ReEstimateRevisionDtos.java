package com.repairshop.saas.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Re-estimate workflow wire shapes (shared contract with the Partner App and
 * the Employee App — field names must not drift).
 */
public final class ReEstimateRevisionDtos {

    private ReEstimateRevisionDtos() {}

    /** One re-estimate (technician request → partner revision → decision). */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Revision {
        private UUID id;
        private UUID ticketId;
        private Integer revisionNo;
        /** TECHNICIAN | PARTNER */
        private String source;
        /** REQUESTED | AWAITING_APPROVAL | APPROVED | REJECTED | DISMISSED */
        private String status;
        private String technicianName;
        private Instant requestedAt;
        private String additionalIssue;
        private String reason;
        private BigDecimal proposedAmount;
        private String technicianNotes;
        private List<String> requestImages;
        private Instant submittedAt;
        private List<Item> items;
        private BigDecimal additionalAmount;
        private BigDecimal originalEstimatedPrice;
        private BigDecimal revisedTotal;
        private String revisionReason;
        private List<String> revisionImages;
        private Instant decidedAt;
        private String decidedByName;
        /** APPROVED | REJECTED | DISMISSED, null while open */
        private String decision;
        private String decisionReason;
        /** SHOP | CUSTOMER_APP */
        private String decidedVia;
        private Instant technicianAcknowledgedAt;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Item {
        private String id;
        private String code;
        private String label;
        private BigDecimal amount;
        private String warranty;
    }

    /** POST /tickets/{id}/re-estimate-requests (assigned technician). */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class TechnicianRequest {
        private String additionalIssue;
        private String reason;
        private BigDecimal proposedAmount;
        private String technicianNotes;
        /** JSON array of URLs, as a string (or a plain array). */
        private Object imagesJson;
    }

    /** POST /tickets/{id}/re-estimates/{revisionId}/decision (owner / shop). */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Decision {
        /** APPROVED | REJECTED */
        private String decision;
        private String reason;
    }

    /** POST /tickets/{id}/re-estimate-requests/{revisionId}/dismiss (owner / shop). */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Reason {
        private String reason;
    }
}
