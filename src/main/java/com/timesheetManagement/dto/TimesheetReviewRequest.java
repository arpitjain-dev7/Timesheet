package com.timesheetManagement.dto;

import com.timesheetManagement.entity.TimesheetStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for a manager to APPROVE or REJECT a submitted timesheet.
 * A rejection comment is strongly encouraged (validated if status = REJECTED).
 */
@Data
public class TimesheetReviewRequest {

    @NotNull(message = "Status is required (APPROVED or REJECTED)")
    private TimesheetStatus status;

    @Size(max = 500, message = "Comment must not exceed 500 characters")
    private String comment;

    @AssertTrue(message = "A comment is required when rejecting a timesheet")
    private boolean isCommentPresentOnRejection() {
        if (status == TimesheetStatus.REJECTED) {
            return comment != null && !comment.isBlank();
        }
        return true;
    }
}

