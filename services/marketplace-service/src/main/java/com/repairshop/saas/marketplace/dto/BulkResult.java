package com.repairshop.saas.marketplace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkResult {
    private UUID batchId;
    private int created;
    private int existing;
    private int failed;
    private int published;
    private int drafts;
    private List<ItemResult> results;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ItemResult {
        private int index;
        private String clientRef;
        private String outcome; // CREATED | EXISTING | FAILED
        private String status;
        private UUID productId;
        private List<String> errors;
        private List<String> warnings;
    }
}
