package com.repairshop.saas.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Single day attendance record")
public class AttendanceRecordResponse {

    @Schema(description = "Date")
    private LocalDate date;

    @Schema(description = "Day of week label")
    private String dayLabel;

    @Schema(description = "Check-in time")
    private LocalTime checkInTime;

    @Schema(description = "Check-out time")
    private LocalTime checkOutTime;

    @Schema(description = "Status: GENERAL, LEAVE, WEEK_OFF, LATE, PERMISSION")
    private String status;

    @Schema(description = "Working hours as string e.g. 12:00:00")
    private String workingHours;

    @Schema(description = "Notes e.g. Permission - 1hrs")
    private String notes;

    @Schema(description = "Minutes the check-in was after the technician's duty start (0 when on time or no duty time configured)")
    private Integer lateMinutes;
}
