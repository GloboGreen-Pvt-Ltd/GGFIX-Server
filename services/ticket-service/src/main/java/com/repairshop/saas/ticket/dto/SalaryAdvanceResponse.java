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
@Schema(description = "Salary advance record")
public class SalaryAdvanceResponse {

    @Schema(description = "Advance ID")
    private UUID id;

    @Schema(description = "Amount")
    private BigDecimal amount;

    @Schema(description = "Advance date")
    private LocalDate advanceDate;

    @Schema(description = "Status: PAID, UNPAID")
    private String status;

    @Schema(description = "Request date & time")
    private Instant requestedAt;

    @Schema(description = "Notes")
    private String notes;
}
