package com.timesheetManagement.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/** Request body for creating a new timesheet (USER). */
@Data
public class TimesheetCreateRequest {

    @Size(max = 150, message = "Title must not exceed 150 characters")
    private String title;

    /** Optional — start of the period covered by this timesheet (inclusive). */
    private LocalDate periodStart;

    /** Optional — end of the period covered by this timesheet (inclusive). */
    private LocalDate periodEnd;
}

