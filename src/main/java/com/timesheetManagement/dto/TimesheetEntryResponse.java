package com.timesheetManagement.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Read-only view of a single {@code TimesheetEntry}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimesheetEntryResponse {

    private Long       id;
    private Long       projectId;
    private String     projectName;
    private String     projectCode;
    private LocalDate  workDate;
    private BigDecimal hoursWorked;
    private String     description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

