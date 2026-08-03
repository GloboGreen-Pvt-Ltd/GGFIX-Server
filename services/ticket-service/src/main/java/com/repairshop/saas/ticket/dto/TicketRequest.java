package com.repairshop.saas.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Create or update ticket")
public class TicketRequest {

    @NotNull
    @Schema(description = "Customer ID", required = true)
    private UUID customerId;

    @Schema(description = "Customer name (denormalized for booking list)")
    private String customerName;

    @Schema(description = "Customer phone (denormalized for booking list)")
    private String customerPhone;

    @Schema(description = "Device brand ID (master_brands)")
    private UUID brandId;

    @Schema(description = "Device model ID (master_models)")
    private UUID modelId;

    @Schema(description = "RAM option ID")
    private UUID ramOptionId;

    @Schema(description = "Storage option ID")
    private UUID storageOptionId;

    @Schema(description = "Device color")
    private String color;

    @Schema(description = "IMEI")
    private String imei;

    @Schema(description = "Issue description")
    private String issueDescription;

    @Schema(description = "Hosted URL of the customer's voice-note recording of the issue " +
            "(Cloudinary). Optional — the booking flow accepts text, audio, or both.")
    private String issueAudioUrl;

    @Schema(description = "Estimated price")
    private BigDecimal estimatedPrice;

    @Schema(description = "Device display name override")
    private String deviceDisplayName;

    @Schema(description = "Device image URL")
    private String deviceImageUrl;

    @Schema(description = "Repair services summary text")
    private String repairServicesSummary;

    @Schema(description = "Price items JSON (array of {label, amount})")
    private String priceItemsJson;

    @Schema(description = "Missing / damaged parts JSON")
    private String missingPartsJson;

    @Schema(description = "Device photos metadata JSON")
    private String devicePhotosJson;

    @Schema(description = "Device security type (NONE, PATTERN, PIN, PASSWORD)")
    private String deviceSecurityType;

    @Schema(description = "Device security value (pattern, PIN, password)")
    private String deviceSecurityValue;

    @Schema(description = "Customer repair approval flag")
    private Boolean customerApproval;

    @Schema(description = "Estimated ready at (start of repair window)")
    private Instant estimatedReadyAt;

    @Schema(description = "Estimated delivery at (when device will be ready)")
    private Instant estimatedDeliveryAt;
}
