package com.timesheetManagement.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for adding a time entry to a DRAFT timesheet.
 *
 * <p>Work duration is expressed as decimal hours (e.g. 2.5 = 2 h 30 min).
 * Minimum 0.5 h, maximum 24 h. The service additionally checks that the
 * user's total logged hours on {@code workDate} do not exceed 24 h.
 */
@Data
public class TimesheetEntryRequest {

    @NotNull(message = "Project ID is required")
    private Long projectId;

    @NotNull(message = "Work date is required")
    private LocalDate workDate;

    @NotNull(message = "Hours worked is required")
    @DecimalMin(value = "0.5",  inclusive = true,  message = "Minimum hours per entry is 0.5")
    @DecimalMax(value = "24.0", inclusive = true,  message = "Maximum hours per entry is 24")
    @Digits(integer = 2, fraction = 2,
            message = "Hours worked must have at most 2 integer digits and 2 decimal places")
    private BigDecimal hoursWorked;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;
}

