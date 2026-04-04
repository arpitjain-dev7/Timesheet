package com.timesheetManagement.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * Request payload for creating a new {@code Timesheet}.
 *
 * <p>{@code weekStartDate} must be a Monday — enforced in the service layer.
 */
@Data
public class TimesheetRequestDTO {

    @NotNull(message = "Week start date is required")
    private LocalDate weekStartDate;
}

