package com.timesheetManagement.service;

import com.timesheetManagement.dto.TimesheetCreateRequest;
import com.timesheetManagement.dto.TimesheetEntryRequest;
import com.timesheetManagement.dto.TimesheetEntryResponse;
import com.timesheetManagement.dto.TimesheetResponse;
import com.timesheetManagement.entity.*;
import com.timesheetManagement.exception.*;
import com.timesheetManagement.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TimesheetService {

    private static final BigDecimal MAX_HOURS_PER_DAY = new BigDecimal("24");

    private final TimesheetRepository      timesheetRepository;
    private final TimesheetEntryRepository entryRepository;
    private final ProjectRepository        projectRepository;
    private final ProjectAssignmentRepository assignmentRepository;
    private final UserRepository           userRepository;

    // ══════════════════════════════════════════════════════════════════════
    //  CREATE TIMESHEET
    // ══════════════════════════════════════════════════════════════════════
    @Transactional
    public TimesheetResponse createTimesheet(TimesheetCreateRequest req, String username) {
        log.info("[TS_CREATE] username='{}'", username);
        User user = findUser(username);

        Timesheet ts = Timesheet.builder()
                .user(user)
                .title(req.getTitle())
                .periodStart(req.getPeriodStart())
                .periodEnd(req.getPeriodEnd())
                .status(TimesheetStatus.DRAFT)
                .build();

        Timesheet saved = timesheetRepository.save(ts);
        log.info("[TS_CREATE] ✅ id={}, username='{}'", saved.getId(), username);
        return toResponse(saved);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ADD ENTRY
    // ══════════════════════════════════════════════════════════════════════
    @Transactional
    public TimesheetResponse addTimesheetEntry(Long timesheetId,
                                               TimesheetEntryRequest req,
                                               String username) {
        log.info("[ENTRY_ADD] timesheetId={}, username='{}'", timesheetId, username);

        Timesheet ts = findWithEntries(timesheetId);
        assertOwner(ts, username);
        assertDraft(ts);

        // ── Business rule: user must be assigned to the project ───────────
        if (!assignmentRepository.existsByProjectIdAndUserId(req.getProjectId(), ts.getUser().getId())) {
            throw new ForbiddenOperationException(
                    "You are not assigned to project id: " + req.getProjectId());
        }

        // ── Business rule: no duplicate (timesheet + project + date) ──────
        if (entryRepository.existsByTimesheetIdAndProjectIdAndWorkDate(
                timesheetId, req.getProjectId(), req.getWorkDate())) {
            throw new DuplicateEntryException(
                    "An entry for this project on " + req.getWorkDate()
                            + " already exists in this timesheet");
        }

        // ── Business rule: total hours on that date must not exceed 24h ───
        BigDecimal existingHours = entryRepository.sumHoursByUserAndDate(
                ts.getUser().getId(), req.getWorkDate());
        if (existingHours.add(req.getHoursWorked()).compareTo(MAX_HOURS_PER_DAY) > 0) {
            throw new InvalidTimesheetStateException(
                    "Cannot log " + req.getHoursWorked() + " h on " + req.getWorkDate()
                            + " — total would exceed 24 h/day (already logged: "
                            + existingHours + " h)");
        }

        Project project = projectRepository.findById(req.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found: " + req.getProjectId()));
        if (!project.isActive()) {
            throw new IllegalArgumentException(
                    "Project '" + project.getName() + "' is inactive");
        }

        TimesheetEntry entry = TimesheetEntry.builder()
                .timesheet(ts)
                .project(project)
                .workDate(req.getWorkDate())
                .hoursWorked(req.getHoursWorked())
                .description(req.getDescription())
                .build();

        ts.getEntries().add(entry);
        Timesheet saved = timesheetRepository.save(ts);
        log.info("[ENTRY_ADD] ✅ entry added to timesheetId={}", timesheetId);
        return toResponse(saved);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SUBMIT
    // ══════════════════════════════════════════════════════════════════════
    @Transactional
    public TimesheetResponse submitTimesheet(Long timesheetId, String username) {
        log.info("[TS_SUBMIT] id={}, username='{}'", timesheetId, username);

        Timesheet ts = findWithEntries(timesheetId);
        assertOwner(ts, username);

        if (ts.getStatus() != TimesheetStatus.DRAFT) {
            throw new InvalidTimesheetStateException(
                    "Only DRAFT timesheets can be submitted. Current status: " + ts.getStatus());
        }
        if (ts.getEntries().isEmpty()) {
            throw new InvalidTimesheetStateException(
                    "Cannot submit an empty timesheet. Add at least one entry first.");
        }

        ts.setStatus(TimesheetStatus.SUBMITTED);
        ts.setSubmittedAt(LocalDateTime.now());

        Timesheet saved = timesheetRepository.save(ts);
        log.info("[TS_SUBMIT] ✅ id={} submitted", timesheetId);
        return toResponse(saved);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  READ
    // ══════════════════════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public Page<TimesheetResponse> getUserTimesheets(String username, Pageable pageable) {
        User user = findUser(username);
        log.debug("[TS_LIST] username='{}', page={}", username, pageable.getPageNumber());
        return timesheetRepository.findAllByUser(user, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public TimesheetResponse getById(Long id, String username, boolean isAdminOrManager) {
        Timesheet ts = findWithEntries(id);
        if (!isAdminOrManager && !ts.getUser().getUsername().equals(username)) {
            throw new ForbiddenOperationException("You do not have access to this timesheet");
        }
        return toResponse(ts);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  DELETE DRAFT
    // ══════════════════════════════════════════════════════════════════════
    @Transactional
    public void deleteTimesheet(Long id, String username) {
        Timesheet ts = findById(id);
        assertOwner(ts, username);
        assertDraft(ts);
        timesheetRepository.delete(ts);
        log.info("[TS_DELETE] ✅ id={} deleted", id);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  REMOVE ENTRY (from DRAFT)
    // ══════════════════════════════════════════════════════════════════════
    @Transactional
    public TimesheetResponse removeEntry(Long timesheetId, Long entryId, String username) {
        Timesheet ts = findWithEntries(timesheetId);
        assertOwner(ts, username);
        assertDraft(ts);

        boolean removed = ts.getEntries().removeIf(e -> e.getId().equals(entryId));
        if (!removed) {
            throw new ResourceNotFoundException(
                    "Entry not found with id: " + entryId + " in timesheet: " + timesheetId);
        }

        Timesheet saved = timesheetRepository.save(ts);
        log.info("[ENTRY_REMOVE] ✅ entryId={} removed from timesheetId={}", entryId, timesheetId);
        return toResponse(saved);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Guard assertions
    // ══════════════════════════════════════════════════════════════════════
    private void assertOwner(Timesheet ts, String username) {
        if (!ts.getUser().getUsername().equals(username)) {
            throw new ForbiddenOperationException("You do not have access to this timesheet");
        }
    }

    private void assertDraft(Timesheet ts) {
        if (ts.getStatus() != TimesheetStatus.DRAFT) {
            throw new InvalidTimesheetStateException(
                    "This operation requires DRAFT status. Current status: " + ts.getStatus());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Private finders
    // ══════════════════════════════════════════════════════════════════════
    private Timesheet findById(Long id) {
        return timesheetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Timesheet not found with id: " + id));
    }

    Timesheet findWithEntries(Long id) {
        return timesheetRepository.findByIdWithEntries(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Timesheet not found with id: " + id));
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + username));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Mappers
    // ══════════════════════════════════════════════════════════════════════
    TimesheetResponse toResponse(Timesheet ts) {
        List<TimesheetEntryResponse> entries = ts.getEntries().stream()
                .sorted(Comparator.comparing(TimesheetEntry::getWorkDate)
                        .thenComparingLong(TimesheetEntry::getId))
                .map(this::toEntryResponse)
                .toList();

        BigDecimal total = ts.getEntries().stream()
                .map(TimesheetEntry::getHoursWorked)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return TimesheetResponse.builder()
                .id(ts.getId())
                .userId(ts.getUser().getId())
                .username(ts.getUser().getUsername())
                .title(ts.getTitle())
                .periodStart(ts.getPeriodStart())
                .periodEnd(ts.getPeriodEnd())
                .status(ts.getStatus())
                .totalHours(total)
                .reviewerComment(ts.getReviewerComment())
                .reviewedByUsername(ts.getReviewedBy() != null
                        ? ts.getReviewedBy().getUsername() : null)
                .submittedAt(ts.getSubmittedAt())
                .reviewedAt(ts.getReviewedAt())
                .entries(entries)
                .createdAt(ts.getCreatedAt())
                .updatedAt(ts.getUpdatedAt())
                .build();
    }

    private TimesheetEntryResponse toEntryResponse(TimesheetEntry e) {
        return TimesheetEntryResponse.builder()
                .id(e.getId())
                .projectId(e.getProject().getId())
                .projectName(e.getProject().getName())
                .projectCode(e.getProject().getCode())
                .workDate(e.getWorkDate())
                .hoursWorked(e.getHoursWorked())
                .description(e.getDescription())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
