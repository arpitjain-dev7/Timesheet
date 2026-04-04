package com.timesheetManagement.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.timesheetManagement.entity.TimesheetStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Read-only view of a {@code Timesheet} including all its entries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimesheetResponseDTO {

    private Long             id;

    // ── Owner ──────────────────────────────────────────────────────────────
    private Long             userId;
    private String           username;

    // ── Week window ────────────────────────────────────────────────────────
    private LocalDate        weekStartDate;
    /** Always weekStartDate + 6 days (Sunday). Computed by the service. */
    private LocalDate        weekEndDate;

    // ── Status & summary ───────────────────────────────────────────────────
    private TimesheetStatus  status;
    private BigDecimal       totalHours;

    // ── Review info ────────────────────────────────────────────────────────
    private String           reviewerRemarks;
    private LocalDateTime    submittedAt;
    private LocalDateTime    reviewedAt;

    // ── Entries ────────────────────────────────────────────────────────────
    private List<TimesheetEntryResponseDTO> entries;

    // ── Audit ──────────────────────────────────────────────────────────────
    private LocalDateTime    createdAt;
    private LocalDateTime    updatedAt;
}

