package com.repairshop.saas.ticket.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class TechnicianKycDocumentDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TechnicianKycDocumentResponse {
        private UUID id;
        private UUID technicianId;
        private String docType;
        private String title;
        private String url;
        private Boolean required;
        private String status;
        private String rejectReason;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TechnicianKycDocumentInput {
        private String docType;
        private String title;
        private String url;
        private Boolean required;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SaveTechnicianKycRequest {
        private List<TechnicianKycDocumentInput> documents;
    }
}
