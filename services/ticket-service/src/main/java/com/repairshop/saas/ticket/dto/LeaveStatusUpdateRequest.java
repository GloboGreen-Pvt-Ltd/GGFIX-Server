package com.repairshop.saas.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body for PATCH /technicians/{id}/leaves/{leaveId}. The same shape covers
 * the spec's separate /approve and /reject endpoints — controller dispatches
 * on `status` so the mobile client only needs one HTTP path.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Approve or reject a leave request")
public class LeaveStatusUpdateRequest {

    @Schema(description = "Target status: APPROVED or REJECTED", required = true)
    private String status;

    @Schema(description = "Optional approver remarks (stored when APPROVED)")
    private String remarks;

    @Schema(description = "Required when REJECTED — explanation shown to the employee")
    private String rejectionReason;
}
