package com.timesheetManagement.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Read-only view of a {@code Project}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectResponse {

    private Long          id;
    private String        name;
    private String        description;
    private String        code;
    private boolean       active;
    private LocalDate     startDate;
    private LocalDate     endDate;
    private String        createdByUsername;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

