package com.repairshop.saas.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Ticket response")
public class TicketResponse {

    @Schema(description = "Ticket ID")
    private UUID id;

    @Schema(description = "Shop ID")
    private UUID shopId;

    @Schema(description = "Customer ID")
    private UUID customerId;

    @Schema(description = "Customer name (denormalized)")
    private String customerName;

    @Schema(description = "Customer phone (denormalized)")
    private String customerPhone;

    @Schema(description = "Customer pickup/postal address (denormalized snapshot)")
    private String customerAddress;

    @Schema(description = "Assigned technician ID")
    private UUID assignedTechnicianId;

    @Schema(description = "Timestamp of the assigned technician's explicit accept action; NULL while awaiting acceptance")
    private java.time.Instant technicianAcceptedAt;

    @Schema(description = "Tracking ID")
    private String trackingId;

    @Schema(description = "Brand ID")
    private UUID brandId;

    @Schema(description = "Model ID")
    private UUID modelId;

    @Schema(description = "RAM option ID")
    private UUID ramOptionId;

    @Schema(description = "Storage option ID")
    private UUID storageOptionId;

    @Schema(description = "Color")
    private String color;

    @Schema(description = "Status")
    private String status;

    @Schema(description = "Estimated price")
    private BigDecimal estimatedPrice;

    @Schema(description = "Final price")
    private BigDecimal finalPrice;

    @Schema(description = "Issue description")
    private String issueDescription;

    @Schema(description = "Voice-note recording of the issue (Cloudinary URL). Null when none.")
    private String issueAudioUrl;

    @Schema(description = "Created at")
    private Instant createdAt;

    @Schema(description = "Updated at")
    private Instant updatedAt;

    @Schema(description = "Device display name")
    private String deviceDisplayName;

    @Schema(description = "Device image URL")
    private String deviceImageUrl;

    @Schema(description = "Repair services summary")
    private String repairServicesSummary;

    @Schema(description = "Price items JSON")
    private String priceItemsJson;

    @Schema(description = "Missing parts JSON")
    private String missingPartsJson;

    @Schema(description = "Device photos JSON")
    private String devicePhotosJson;

    @Schema(description = "Technician-uploaded post-acceptance photos JSON (array of URLs)")
    private String technicianPhotosJson;

    @Schema(description = "Device security type")
    private String deviceSecurityType;

    @Schema(description = "Device security value")
    private String deviceSecurityValue;

    @Schema(description = "Customer repair approval flag")
    private Boolean customerApproval;

    @Schema(description = "Estimated ready at")
    private Instant estimatedReadyAt;

    @Schema(description = "Estimated delivery at")
    private Instant estimatedDeliveryAt;

    @Schema(description = "Assigned technician name (denormalized for customer-side render)")
    private String assignedTechnicianName;

    @Schema(description = "Short uppercase technician code (first 8 chars of technician id)")
    private String assignedTechnicianCode;

    // ---- Latest customer-visible "Issue Verified & Updated" note ----
    // Populated from repair_notes by TicketService.toResponse, taking the
    // most recent row with is_internal=false. Customer + owner detail
    // screens render these on the "Technician Issue Verified & Updated"
    // card so the verification is visible without a second API call.

    @Schema(description = "Latest customer-visible compliance note text from repair_notes; null when none has been submitted")
    private String complianceNote;

    @Schema(description = "Cloudinary URL of the voice note attached to the latest compliance note; null when none")
    private String complianceAudioUrl;

    @Schema(description = "Image URLs attached to the latest compliance note; empty list when none")
    private List<String> complianceImageUrls;

    @Schema(description = "Timestamp at which the latest compliance note was created")
    private Instant complianceVerifiedAt;
}
