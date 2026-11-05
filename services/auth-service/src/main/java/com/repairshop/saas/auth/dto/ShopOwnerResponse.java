package com.repairshop.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Result of creating a shop owner with shop locations")
public class ShopOwnerResponse {
    private UUID ownerId;
    private String email;
    private String name;
    private List<ShopSummary> shops;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShopSummary {
        private UUID id;
        private String name;
        private String slug;
    }
}
