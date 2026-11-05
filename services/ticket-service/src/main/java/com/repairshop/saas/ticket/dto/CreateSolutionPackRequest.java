package com.repairshop.saas.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Attach a solution pack (reference or new) to a ticket")
public class CreateSolutionPackRequest {

    @NotBlank
    @Schema(description = "'REFERENCE' (existing solution viewed) or 'NEW' (technician's new upload)")
    private String packType;

    private String title;
    private String description;

    @Schema(description = "URL of the uploaded file (from /media/upload). Legacy single-file form.")
    private String fileUrl;

    @Schema(description = "Original filename for display. Legacy single-file form.")
    private String fileName;

    @Schema(description = "Optional brand id (master catalog) the solution targets")
    private UUID brandId;

    @Schema(description = "Optional model id (master catalog) the solution targets")
    private UUID modelId;

    @Schema(description = "Denormalized brand label for display when the master catalog row is unavailable")
    private String brandName;

    @Schema(description = "Denormalized model label for display when the master catalog row is unavailable")
    private String modelName;

    @Schema(description = "Top-level issue category label (denormalized name from master_repair_categories)")
    private String issueCategory;

    @Schema(description = "Issue sub-category label (denormalized name from master_repair_services)")
    private String issueSubcategory;

    @Schema(description = "master_repair_categories.id this pack targets")
    private UUID issueCategoryId;

    @Schema(description = "master_repair_services.id this pack targets")
    private UUID issueSubcategoryId;

    @Schema(description = "JSON array of attachments: [{ type: 'audio'|'video'|'image', url, name }]")
    private String filesJson;
}
