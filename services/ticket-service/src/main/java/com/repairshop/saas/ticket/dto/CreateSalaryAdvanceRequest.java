package com.repairshop.saas.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Create salary advance")
public class CreateSalaryAdvanceRequest {

    @Schema(description = "Amount", required = true)
    private BigDecimal amount;

    @Schema(description = "Advance date")
    private LocalDate advanceDate;

    @Schema(description = "Notes")
    private String notes;
}
