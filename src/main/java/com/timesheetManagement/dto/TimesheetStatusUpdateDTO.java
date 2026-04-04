package com.timesheetManagement.dto;

import com.timesheetManagement.entity.TimesheetStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request payload for an admin to APPROVE or REJECT a submitted timesheet.
 */
@Data
public class TimesheetStatusUpdateDTO {

    /**
     * Must be {@code APPROVED} or {@code REJECTED}.
     * Any other value is rejected at the service layer.
     */
    @NotNull(message = "Status is required (APPROVED or REJECTED)")
    private TimesheetStatus status;

    /** Optional reviewer note — required when rejecting (encouraged for clarity). */
    @Size(max = 500, message = "Remarks must not exceed 500 characters")
    private String remarks;
}

