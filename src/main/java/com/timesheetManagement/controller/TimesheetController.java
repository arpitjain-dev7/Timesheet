package com.timesheetManagement.controller;

import com.timesheetManagement.dto.TimesheetCreateRequest;
import com.timesheetManagement.dto.TimesheetEntryRequest;
import com.timesheetManagement.dto.TimesheetResponse;
import com.timesheetManagement.service.TimesheetService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/timesheets")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Timesheets", description = "User timesheet lifecycle — create, fill entries, submit")
@SecurityRequirement(name = "bearerAuth")
public class TimesheetController {

    private final TimesheetService timesheetService;

    // ── POST /api/timesheets ──────────────────────────────────────────────
    @Operation(summary = "Create a new timesheet (USER)")
    @PostMapping
    @PreAuthorize("hasAnyRole('USER','MANAGER','ADMIN')")
    public ResponseEntity<TimesheetResponse> createTimesheet(
            @Valid @RequestBody TimesheetCreateRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("POST /api/timesheets");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(timesheetService.createTimesheet(req, userDetails.getUsername()));
    }

    // ── GET /api/timesheets/my ────────────────────────────────────────────
    @Operation(summary = "Get all timesheets for the current user (paginated)")
    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('USER','MANAGER','ADMIN')")
    public ResponseEntity<Map<String, Object>> getMyTimesheets(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0")          int page,
            @Parameter(description = "Page size")             @RequestParam(defaultValue = "10")         int size,
            @Parameter(description = "Sort field")            @RequestParam(defaultValue = "createdAt")  String sortBy,
            @Parameter(description = "asc | desc")            @RequestParam(defaultValue = "desc")       String sortDir,
            @AuthenticationPrincipal UserDetails userDetails) {

        Sort sort = "asc".equalsIgnoreCase(sortDir)
                ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Page<TimesheetResponse> result = timesheetService.getUserTimesheets(
                userDetails.getUsername(), PageRequest.of(page, size, sort));

        return ResponseEntity.ok(Map.of(
                "timesheets",  result.getContent(),
                "currentPage", result.getNumber(),
                "totalItems",  result.getTotalElements(),
                "totalPages",  result.getTotalPages(),
                "isLast",      result.isLast()));
    }

    // ── GET /api/timesheets/{id} ──────────────────────────────────────────
    @Operation(summary = "Get a specific timesheet by ID (owner, MANAGER, or ADMIN)")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','MANAGER','ADMIN')")
    public ResponseEntity<TimesheetResponse> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        boolean elevated = hasElevatedRole(userDetails);
        return ResponseEntity.ok(
                timesheetService.getById(id, userDetails.getUsername(), elevated));
    }

    // ── POST /api/timesheets/{id}/entries — add a time entry ─────────────
    @Operation(summary = "Add a time entry to a DRAFT timesheet (USER)",
               description = "User must be assigned to the project. "
                           + "Duplicate project+date within the same timesheet is rejected. "
                           + "Total hours per day across active timesheets must not exceed 24.")
    @PostMapping("/{id}/entries")
    @PreAuthorize("hasAnyRole('USER','MANAGER','ADMIN')")
    public ResponseEntity<TimesheetResponse> addEntry(
            @PathVariable Long id,
            @Valid @RequestBody TimesheetEntryRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("POST /api/timesheets/{}/entries", id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(timesheetService.addTimesheetEntry(id, req, userDetails.getUsername()));
    }

    // ── DELETE /api/timesheets/{id}/entries/{entryId} ─────────────────────
    @Operation(summary = "Remove a time entry from a DRAFT timesheet (owner only)")
    @DeleteMapping("/{id}/entries/{entryId}")
    @PreAuthorize("hasAnyRole('USER','MANAGER','ADMIN')")
    public ResponseEntity<TimesheetResponse> removeEntry(
            @PathVariable Long id,
            @PathVariable Long entryId,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("DELETE /api/timesheets/{}/entries/{}", id, entryId);
        return ResponseEntity.ok(
                timesheetService.removeEntry(id, entryId, userDetails.getUsername()));
    }

    // ── POST /api/timesheets/{id}/submit ──────────────────────────────────
    @Operation(summary = "Submit a DRAFT timesheet for manager review (USER only)",
               description = "Status transitions: DRAFT → SUBMITTED. "
                           + "Timesheet must contain at least one entry.")
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('USER','MANAGER','ADMIN')")
    public ResponseEntity<TimesheetResponse> submit(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("POST /api/timesheets/{}/submit", id);
        return ResponseEntity.ok(
                timesheetService.submitTimesheet(id, userDetails.getUsername()));
    }

    // ── DELETE /api/timesheets/{id} — delete DRAFT ───────────────────────
    @Operation(summary = "Delete a DRAFT timesheet (owner only)")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','MANAGER','ADMIN')")
    public ResponseEntity<Map<String, String>> deleteTimesheet(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("DELETE /api/timesheets/{}", id);
        timesheetService.deleteTimesheet(id, userDetails.getUsername());
        return ResponseEntity.ok(Map.of("message", "Timesheet " + id + " deleted successfully"));
    }

    // ── Helper ────────────────────────────────────────────────────────────
    private boolean hasElevatedRole(UserDetails ud) {
        return ud.getAuthorities().stream()
                .anyMatch(a -> "ROLE_MANAGER".equals(a.getAuthority())
                        || "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
