package com.repairshop.saas.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Monthly attendance summary for an employee")
public class AttendanceSummaryResponse {

    @Schema(description = "Month (1-12)")
    private int month;

    @Schema(description = "Year")
    private int year;

    @Schema(description = "Present days count")
    private int presentDays;

    @Schema(description = "Late hours (e.g. 1.30)")
    private String lateHours;

    @Schema(description = "Permission count")
    private int permissionCount;

    @Schema(description = "Leave days count")
    private int leaveDays;

    @Schema(description = "Holiday count")
    private int holidayCount;

    @Schema(description = "Daily attendance records")
    private List<AttendanceRecordResponse> dailyRecords;
}
