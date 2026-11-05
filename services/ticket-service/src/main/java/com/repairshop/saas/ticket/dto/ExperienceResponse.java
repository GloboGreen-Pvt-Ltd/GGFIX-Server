package com.repairshop.saas.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Work experience record")
public class ExperienceResponse {

    @Schema(description = "Experience ID")
    private UUID id;

    @Schema(description = "Shop name")
    private String shopName;

    @Schema(description = "Location")
    private String location;

    @Schema(description = "Join date")
    private LocalDate joinDate;

    @Schema(description = "Relieving date (null = Present)")
    private LocalDate relievingDate;

    @Schema(description = "Working type: FULL_TIME, PART_TIME")
    private String workingType;

    @Schema(description = "Last salary")
    private String lastSalary;

    @Schema(description = "Total duration e.g. 1 Year 0 Months")
    private String totalDuration;

    @Schema(description = "Total service count")
    private Integer totalService;

    @Schema(description = "Completed count")
    private Integer completedCount;

    @Schema(description = "Return count")
    private Integer returnCount;
}
