package com.timesheetManagement.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Read-only view of a {@code Project}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectResponseDTO {

    private Long          id;
    private String        code;
    private String        name;
    private String        description;
    private boolean       active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

