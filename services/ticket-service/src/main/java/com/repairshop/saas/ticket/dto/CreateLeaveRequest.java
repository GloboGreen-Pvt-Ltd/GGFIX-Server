package com.repairshop.saas.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Apply for leave")
public class CreateLeaveRequest {

    @Schema(description = "Leave type: CASUAL_LEAVE | SICK_LEAVE | EMERGENCY_LEAVE | PERMISSION | HALF_DAY | OTHER")
    private String leaveType;

    @NotNull
    @Schema(description = "Start date", required = true)
    private LocalDate startDate;

    @NotNull
    @Schema(description = "End date", required = true)
    private LocalDate endDate;

    @Schema(description = "Total days (optional — server recomputes when omitted; allows half-day 0.5)")
    private BigDecimal totalDays;

    @Schema(description = "Reason / description")
    private String reason;

    @Schema(description = "Optional attachment URL (e.g. medical proof)")
    private String attachmentUrl;
}
