package com.timesheetManagement.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * Request body for updating an existing project.
 * All fields are optional — only non-null fields will be applied (partial update).
 */
@Data
public class ProjectUpdateRequest {

    @Size(max = 100, message = "Project name must not exceed 100 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    /** If provided, sets the project start date. */
    private LocalDate startDate;

    /** If provided, sets the project end date. */
    private LocalDate endDate;

    /**
     * If provided, activates (true) or deactivates (false) the project.
     * Null means no change.
     */
    private Boolean active;
}

