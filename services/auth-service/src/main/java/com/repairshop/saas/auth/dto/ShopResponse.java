package com.repairshop.saas.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopResponse {
    private UUID id;
    private String name;
    private String slug;
    private Boolean isActive;
    /** For admin UI: ACTIVE or SUSPENDED */
    private String status;
}
