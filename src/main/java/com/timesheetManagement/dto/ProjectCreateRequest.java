package com.timesheetManagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/** Request body for creating a new project (MANAGER only). */
@Data
public class ProjectCreateRequest {

    @NotBlank(message = "Project name is required")
    @Size(max = 100, message = "Project name must not exceed 100 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    /** Optional short code, e.g. "PROJ-001". Must be uppercase alphanumeric. */
    @Size(max = 20, message = "Code must not exceed 20 characters")
    @Pattern(
        regexp  = "^[A-Z0-9_-]*$",
        message = "Code must contain only uppercase letters, digits, hyphens, or underscores"
    )
    private String code;

    private LocalDate startDate;

    private LocalDate endDate;
}

