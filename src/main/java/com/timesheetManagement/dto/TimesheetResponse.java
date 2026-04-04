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

/** Read-only view of a {@code Timesheet} with all its entries. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimesheetResponse {

    private Long             id;

    // ── Owner ──────────────────────────────────────────────────────────────
    private Long             userId;
    private String           username;

    // ── Period ─────────────────────────────────────────────────────────────
    private String           title;
    private LocalDate        periodStart;
    private LocalDate        periodEnd;

    // ── Status ─────────────────────────────────────────────────────────────
    private TimesheetStatus  status;

    /** Sum of all entry hours — computed by the service. */
    private BigDecimal       totalHours;

    // ── Review info ────────────────────────────────────────────────────────
    private String           reviewerComment;
    private String           reviewedByUsername;
    private LocalDateTime    submittedAt;
    private LocalDateTime    reviewedAt;

    // ── Entries (sorted by workDate ASC) ───────────────────────────────────
    private List<TimesheetEntryResponse> entries;

    // ── Audit ──────────────────────────────────────────────────────────────
    private LocalDateTime    createdAt;
    private LocalDateTime    updatedAt;
}

