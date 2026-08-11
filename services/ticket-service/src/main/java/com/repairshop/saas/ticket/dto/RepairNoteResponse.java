package com.repairshop.saas.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Repair note attached to a ticket")
public class RepairNoteResponse {

    private UUID id;
    private UUID ticketId;
    private UUID authorId;
    private String note;
    private Boolean isInternal;
    private String audioUrl;
    private List<String> imageUrls;
    private Instant createdAt;

    @Schema(description = "Last edit time. Equals createdAt until the note is edited — the "
            + "technician's Submitted Notes list uses the gap to show an 'Edited' marker.")
    private Instant updatedAt;
}
