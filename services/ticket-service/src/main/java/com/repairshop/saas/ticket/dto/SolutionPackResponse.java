package com.repairshop.saas.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Solution pack attached to a ticket")
public class SolutionPackResponse {

    private UUID id;
    private UUID ticketId;
    private UUID shopId;
    private String packType;
    private String title;
    private String description;
    private String fileUrl;
    private String fileName;
    private UUID uploadedBy;
    private UUID brandId;
    private UUID modelId;
    private String brandName;
    private String modelName;
    private String issueCategory;
    private String issueSubcategory;
    private UUID issueCategoryId;
    private UUID issueSubcategoryId;
    private String filesJson;
    private Instant createdAt;
}
