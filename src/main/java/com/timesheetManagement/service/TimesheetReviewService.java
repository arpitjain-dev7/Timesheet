package com.timesheetManagement.service;

import com.timesheetManagement.dto.TimesheetResponse;
import com.timesheetManagement.dto.TimesheetReviewRequest;
import com.timesheetManagement.entity.Timesheet;
import com.timesheetManagement.entity.TimesheetStatus;
import com.timesheetManagement.entity.User;
import com.timesheetManagement.exception.ForbiddenOperationException;
import com.timesheetManagement.exception.InvalidTimesheetStateException;
import com.timesheetManagement.exception.ResourceNotFoundException;
import com.timesheetManagement.repository.TimesheetRepository;
import com.timesheetManagement.repository.TimesheetSpecification;
import com.timesheetManagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class TimesheetReviewService {

    private final TimesheetRepository timesheetRepository;
    private final UserRepository      userRepository;
    private final TimesheetService    timesheetService; // for shared mapper

    // ══════════════════════════════════════════════════════════════════════
    //  APPROVE
    // ══════════════════════════════════════════════════════════════════════
    @Transactional
    public TimesheetResponse approveTimesheet(Long id, String managerUsername) {
        log.info("[TS_APPROVE] id={}, manager='{}'", id, managerUsername);

        Timesheet ts    = findWithEntries(id);
        User      mgr   = findUser(managerUsername);

        assertSubmitted(ts);

        // ── Business rule: manager cannot approve their own timesheet ─────
        if (ts.getUser().getUsername().equals(managerUsername)) {
            throw new ForbiddenOperationException(
                    "A manager cannot approve their own timesheet");
        }

        ts.setStatus(TimesheetStatus.APPROVED);
        ts.setReviewedBy(mgr);
        ts.setReviewedAt(LocalDateTime.now());

        Timesheet saved = timesheetRepository.save(ts);
        log.info("[TS_APPROVE] ✅ id={} APPROVED by '{}'", id, managerUsername);
        return timesheetService.toResponse(saved);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  REJECT
    // ══════════════════════════════════════════════════════════════════════
    @Transactional
    public TimesheetResponse rejectTimesheet(Long id,
                                             TimesheetReviewRequest req,
                                             String managerUsername) {
        log.info("[TS_REJECT] id={}, manager='{}'", id, managerUsername);

        Timesheet ts  = findWithEntries(id);
        User      mgr = findUser(managerUsername);

        assertSubmitted(ts);

        if (ts.getUser().getUsername().equals(managerUsername)) {
            throw new ForbiddenOperationException(
                    "A manager cannot reject their own timesheet");
        }

        ts.setStatus(TimesheetStatus.REJECTED);
        ts.setReviewerComment(req.getComment());
        ts.setReviewedBy(mgr);
        ts.setReviewedAt(LocalDateTime.now());

        Timesheet saved = timesheetRepository.save(ts);
        log.info("[TS_REJECT] ✅ id={} REJECTED by '{}'", id, managerUsername);
        return timesheetService.toResponse(saved);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  FILTER TIMESHEETS FOR MANAGER
    //  Optional filters: projectId, userId, dateFrom, dateTo, status
    //
    //  Uses JPA Criteria API (TimesheetSpecification) instead of JPQL to avoid
    //  the PostgreSQL "could not determine data type of parameter $N" error that
    //  occurs when null values are passed to "(:param IS NULL OR col = :param)"
    //  JPQL patterns in prepared statements.
    // ══════════════════════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public Page<TimesheetResponse> filterTimesheetsForManager(
            Long projectId,
            Long userId,
            LocalDate dateFrom,
            LocalDate dateTo,
            TimesheetStatus status,
            Pageable pageable) {

        log.debug("[TS_FILTER] projectId={}, userId={}, from={}, to={}, status={}",
                projectId, userId, dateFrom, dateTo, status);

        return timesheetRepository.findAll(
                        TimesheetSpecification.filter(userId, status, projectId, dateFrom, dateTo),
                        pageable)
                .map(timesheetService::toResponse);
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private Timesheet findWithEntries(Long id) {
        return timesheetRepository.findByIdWithEntries(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Timesheet not found with id: " + id));
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + username));
    }

    private void assertSubmitted(Timesheet ts) {
        if (ts.getStatus() != TimesheetStatus.SUBMITTED) {
            throw new InvalidTimesheetStateException(
                    "Only SUBMITTED timesheets can be reviewed. Current status: "
                            + ts.getStatus());
        }
    }
}

