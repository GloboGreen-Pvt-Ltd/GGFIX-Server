package com.repairshop.saas.shop.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ShopKycDocumentDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ShopKycDocumentResponse {
        private UUID id;
        private UUID shopId;
        private String docType;
        private String title;
        private String url;
        private Boolean required;
        private String status;
        private String rejectReason;
        private Instant createdAt;
        private Instant updatedAt;
    }

    /** Single doc the client wants to upsert. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShopKycDocumentInput {
        private String docType;
        private String title;
        private String url;
        private Boolean required;
    }

    /** Batch upsert body — client sends every doc currently picked. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SaveShopKycRequest {
        private List<ShopKycDocumentInput> documents;
    }
}
