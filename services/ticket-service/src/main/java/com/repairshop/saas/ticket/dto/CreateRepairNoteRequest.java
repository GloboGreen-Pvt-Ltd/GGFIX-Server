package com.repairshop.saas.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Create a repair note on a ticket")
public class CreateRepairNoteRequest {

    @NotBlank
    @Schema(description = "Free-text note (compliance notes from the tech detail screen)")
    private String note;

    @Schema(description = "Hide from customer? Defaults to false (visible to customer + shop)")
    private Boolean isInternal;

    @Schema(description = "Cloudinary URL of the optional voice-note recording attached to this note.")
    private String audioUrl;

    @Schema(description = "Optional list of Cloudinary image URLs attached to this note (up to 3).")
    private List<String> imageUrls;
}
