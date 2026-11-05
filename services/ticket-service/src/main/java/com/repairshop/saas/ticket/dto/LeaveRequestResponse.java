package com.repairshop.saas.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Leave request for an employee")
public class LeaveRequestResponse {

    @Schema(description = "Leave request ID")
    private UUID id;

    @Schema(description = "Leave type")
    private String leaveType;

    @Schema(description = "Start date")
    private LocalDate startDate;

    @Schema(description = "End date")
    private LocalDate endDate;

    @Schema(description = "Total days (supports 0.5 for HALF_DAY)")
    private BigDecimal totalDays;

    @Schema(description = "Reason")
    private String reason;

    @Schema(description = "Optional attachment URL")
    private String attachmentUrl;

    @Schema(description = "Status: PENDING (alias PROCESSING), APPROVED, REJECTED, CANCELLED")
    private String status;

    @Schema(description = "Requested at")
    private Instant requestedAt;

    @Schema(description = "Applied days description e.g. 1 Day, 2nd Half")
    private String appliedDaysLabel;

    @Schema(description = "Technician (employee) ID")
    private UUID technicianId;

    @Schema(description = "Technician (employee) name — populated on shop-side lists")
    private String technicianName;

    @Schema(description = "User ID of approver, if APPROVED")
    private UUID approvedBy;

    @Schema(description = "Approval timestamp")
    private Instant approvedAt;

    @Schema(description = "User ID of rejecter, if REJECTED")
    private UUID rejectedBy;

    @Schema(description = "Rejection timestamp")
    private Instant rejectedAt;

    @Schema(description = "Free-text reason captured when rejecting")
    private String rejectionReason;

    @Schema(description = "Optional approver remarks (notes)")
    private String remarks;
}
