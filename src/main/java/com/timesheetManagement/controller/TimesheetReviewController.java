package com.timesheetManagement.controller;

import com.timesheetManagement.dto.TimesheetResponse;
import com.timesheetManagement.dto.TimesheetReviewRequest;
import com.timesheetManagement.entity.TimesheetStatus;
import com.timesheetManagement.service.TimesheetReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/manager/timesheets")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Timesheet Review", description = "Manager endpoints — filter, approve, reject timesheets")
@SecurityRequirement(name = "bearerAuth")
public class TimesheetReviewController {

    private final TimesheetReviewService reviewService;

    // ── GET /api/manager/timesheets — filter with optional params ─────────
    @Operation(
        summary = "List timesheets with optional filters (MANAGER/ADMIN only)",
        description = """
                All query parameters are optional. Combine freely:
                - **projectId** — only timesheets containing entries for this project
                - **userId**    — only timesheets owned by this user
                - **dateFrom**  — entries on or after this date (ISO-8601 yyyy-MM-dd)
                - **dateTo**    — entries on or before this date
                - **status**    — DRAFT | SUBMITTED | APPROVED | REJECTED
                - **page / size / sortBy / sortDir** — standard pagination

                **Scope rule:**
                - ROLE_MANAGER can only see timesheets of their own direct reports.
                - ROLE_ADMIN can see all timesheets.
                """)
    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<Map<String, Object>> filterTimesheets(
            @Parameter(description = "Filter by project ID")
            @RequestParam(required = false) Long projectId,

            @Parameter(description = "Filter by user ID (manager scope is enforced)")
            @RequestParam(required = false) Long userId,

            @Parameter(description = "Entries on or after (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,

            @Parameter(description = "Entries on or before (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,

            @Parameter(description = "Timesheet status")
            @RequestParam(required = false) TimesheetStatus status,

            @RequestParam(defaultValue = "0")           int page,
            @RequestParam(defaultValue = "10")          int size,
            @RequestParam(defaultValue = "createdAt")   String sortBy,
            @RequestParam(defaultValue = "desc")        String sortDir,

            @AuthenticationPrincipal UserDetails userDetails) {

        Sort sort = "asc".equalsIgnoreCase(sortDir)
                ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();

        Page<TimesheetResponse> result = reviewService.filterTimesheetsForManager(
                userDetails.getUsername(),
                projectId, userId, dateFrom, dateTo, status,
                PageRequest.of(page, size, sort));

        log.info("GET /api/manager/timesheets manager='{}' → total={}",
                userDetails.getUsername(), result.getTotalElements());

        return ResponseEntity.ok(Map.of(
                "timesheets",  result.getContent(),
                "currentPage", result.getNumber(),
                "totalItems",  result.getTotalElements(),
                "totalPages",  result.getTotalPages(),
                "isLast",      result.isLast()));
    }

    // ── PUT /api/manager/timesheets/{id}/approve ──────────────────────────
    @Operation(summary = "Approve a SUBMITTED timesheet (MANAGER only)",
               description = "Manager cannot approve their own timesheet.")
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<TimesheetResponse> approve(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("PUT /api/manager/timesheets/{}/approve", id);
        return ResponseEntity.ok(
                reviewService.approveTimesheet(id, userDetails.getUsername()));
    }

    // ── PUT /api/manager/timesheets/{id}/reject ───────────────────────────
    @Operation(summary = "Reject a SUBMITTED timesheet with a mandatory comment (MANAGER only)",
               description = "Manager cannot reject their own timesheet. "
                           + "A non-blank comment is required.")
    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<TimesheetResponse> reject(
            @PathVariable Long id,
            @Valid @RequestBody TimesheetReviewRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("PUT /api/manager/timesheets/{}/reject", id);
        return ResponseEntity.ok(
                reviewService.rejectTimesheet(id, req, userDetails.getUsername()));
    }
}

