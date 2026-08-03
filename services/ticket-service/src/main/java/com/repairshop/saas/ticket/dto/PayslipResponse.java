package com.repairshop.saas.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Monthly payslip summary")
public class PayslipResponse {

    @Schema(description = "Month (1-12)")
    private int month;

    @Schema(description = "Year")
    private int year;

    @Schema(description = "Period start date")
    private LocalDate periodStart;

    @Schema(description = "Period end date")
    private LocalDate periodEnd;

    @Schema(description = "Present days")
    private int presentDays;

    @Schema(description = "Daily wage days")
    private int dailyWageDays;

    @Schema(description = "Regular salary amount")
    private String regularSalary;

    @Schema(description = "Regular wage amount")
    private String regularWage;

    @Schema(description = "Net salary amount")
    private String netSalary;

    @Schema(description = "Net wage amount")
    private String netWage;
}
