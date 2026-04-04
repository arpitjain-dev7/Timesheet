package com.timesheetManagement.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** Request body for assigning a project to one or more users (MANAGER only). */
@Data
public class ProjectAssignmentRequest {

    @NotNull(message = "Project ID is required")
    private Long projectId;

    @NotEmpty(message = "At least one user ID must be provided")
    private List<Long> userIds;
}

