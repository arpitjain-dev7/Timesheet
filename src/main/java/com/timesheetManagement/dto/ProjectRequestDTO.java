package com.timesheetManagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request payload for creating or updating a {@code Project}.
 */
@Data
public class ProjectRequestDTO {

    @NotBlank(message = "Project code is required")
    @Size(max = 20, message = "Project code must not exceed 20 characters")
    @Pattern(
        regexp  = "^[A-Z0-9_-]+$",
        message = "Project code must contain only uppercase letters, digits, hyphens, or underscores"
    )
    private String code;

    @NotBlank(message = "Project name is required")
    @Size(max = 100, message = "Project name must not exceed 100 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    /** Defaults to {@code true}; set {@code false} to create an already-inactive project. */
    private boolean active = true;
}

