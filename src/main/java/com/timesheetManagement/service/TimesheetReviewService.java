package com.timesheetManagement.service;

import com.timesheetManagement.dto.TimesheetResponse;
import com.timesheetManagement.dto.TimesheetReviewRequest;
import com.timesheetManagement.entity.RoleName;
import com.timesheetManagement.entity.Timesheet;
import com.timesheetManagement.entity.TimesheetEntry;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TimesheetReviewService {

    private final TimesheetRepository timesheetRepository;
    private final UserRepository      userRepository;
    private final TimesheetService    timesheetService; // for shared mapper
    private final EmailService        emailService;     // for status notifications

    // ══════════════════════════════════════════════════════════════════════
    //  APPROVE
    // ══════════════════════════════════════════════════════════════════════
    @Transactional
    public TimesheetResponse approveTimesheet(Long id, String managerUsername) {
        log.info("[TS_APPROVE] id={}, manager='{}'", id, managerUsername);

        Timesheet ts  = findWithEntries(id);
        User      mgr = findUser(managerUsername);

        assertSubmitted(ts);

        // ── Self-approval guard ───────────────────────────────────────────
        if (ts.getUser().getUsername().equals(managerUsername)) {
            log.warn("[TS_APPROVE] manager='{}' attempted to approve their own timesheet id={}",
                    managerUsername, id);
            throw new ForbiddenOperationException(
                    "A manager cannot approve their own timesheet");
        }

        // ── Scope guard: manager may only review their direct reports ─────
        assertManagerCanReview(ts, mgr);

        ts.setStatus(TimesheetStatus.APPROVED);
        ts.setReviewedBy(mgr);
        ts.setReviewedAt(LocalDateTime.now());

        Timesheet saved = timesheetRepository.save(ts);
        log.info("[TS_APPROVE] ✅ id={} APPROVED by '{}'", id, managerUsername);

        // ── Notify the owner asynchronously ──────────────────────────────
        sendStatusEmail(saved, mgr, null);

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

        // ── Self-rejection guard ──────────────────────────────────────────
        if (ts.getUser().getUsername().equals(managerUsername)) {
            log.warn("[TS_REJECT] manager='{}' attempted to reject their own timesheet id={}",
                    managerUsername, id);
            throw new ForbiddenOperationException(
                    "A manager cannot reject their own timesheet");
        }

        // ── Scope guard: manager may only review their direct reports ─────
        assertManagerCanReview(ts, mgr);

        ts.setStatus(TimesheetStatus.REJECTED);
        ts.setReviewerComment(req.getComment());
        ts.setReviewedBy(mgr);
        ts.setReviewedAt(LocalDateTime.now());

        Timesheet saved = timesheetRepository.save(ts);
        log.info("[TS_REJECT] ✅ id={} REJECTED by '{}'", id, managerUsername);

        // ── Notify the owner asynchronously ──────────────────────────────
        sendStatusEmail(saved, mgr, req.getComment());

        return timesheetService.toResponse(saved);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  FILTER TIMESHEETS FOR MANAGER
    //  Optional filters: projectId, userId, dateFrom, dateTo, status
    //
    //  Scope rule:
    //   - ROLE_MANAGER : only sees timesheets of their own direct reports
    //                    (users whose manager_id = this manager's id).
    //   - ROLE_ADMIN   : sees all timesheets (scope check bypassed).
    //
    //  Uses JPA Criteria API (TimesheetSpecification) instead of JPQL to avoid
    //  the PostgreSQL "could not determine data type of parameter $N" error that
    //  occurs when null values are passed to "(:param IS NULL OR col = :param)"
    //  JPQL patterns in prepared statements.
    // ══════════════════════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public Page<TimesheetResponse> filterTimesheetsForManager(
            String managerUsername,
            Long projectId,
            Long userId,
            LocalDate dateFrom,
            LocalDate dateTo,
            TimesheetStatus status,
            Pageable pageable) {

        User manager = findUser(managerUsername);

        boolean isAdmin = manager.getRoles().stream()
                .anyMatch(r -> r.getName() == RoleName.ROLE_ADMIN);

        log.debug("[TS_FILTER] manager='{}', isAdmin={}, projectId={}, userId={}, from={}, to={}, status={}",
                managerUsername, isAdmin, projectId, userId, dateFrom, dateTo, status);

        // Admin sees all timesheets; managers only see their direct reports.
        // Passing null as managerId to the Specification means "no scope restriction".
        Long scopedManagerId = isAdmin ? null : manager.getId();

        // Extra guard: if caller (manager) tries to filter by a specific userId,
        // verify that user is actually one of their direct reports.
        if (!isAdmin && userId != null) {
            User targetUser = userRepository.findById(userId)
                    .orElseThrow(() -> {
                        log.warn("[TS_FILTER] manager='{}' requested timesheets for unknown userId={}",
                                managerUsername, userId);
                        return new ResourceNotFoundException("User not found with id: " + userId);
                    });

            User targetManager = targetUser.getManager();
            if (targetManager == null || !targetManager.getId().equals(manager.getId())) {
                log.warn("[TS_FILTER] manager='{}' (id={}) tried to filter by userId={} " +
                         "who reports to a different manager (managerId={})",
                        managerUsername, manager.getId(), userId,
                        targetManager != null ? targetManager.getId() : "none");
                throw new ForbiddenOperationException(
                        "You can only view timesheets of employees who report directly to you.");
            }
        }

        Page<TimesheetResponse> result = timesheetRepository.findAll(
                        TimesheetSpecification.filter(
                                userId, status, projectId, dateFrom, dateTo, scopedManagerId),
                        pageable)
                .map(timesheetService::toResponse);

        log.info("[TS_FILTER] manager='{}', isAdmin={}, filters=[projectId={}, userId={}, from={}, to={}, status={}] → {} result(s)",
                managerUsername, isAdmin, projectId, userId, dateFrom, dateTo, status,
                result.getTotalElements());

        return result;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  EMAIL NOTIFICATION HELPERS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Extracts all needed data from the saved timesheet <em>within the current
     * transaction</em> (so lazy associations are still reachable), then delegates
     * to {@link EmailService#sendTimesheetStatusEmail} which runs {@code @Async}.
     *
     * <p>Any exception here is swallowed and logged — a failing notification
     * must never roll back the review transaction.
     *
     * @param ts       the just-saved timesheet (entries already eager-loaded)
     * @param reviewer the manager who performed the action
     * @param comment  rejection comment, or {@code null} for approvals
     */
    private void sendStatusEmail(Timesheet ts, User reviewer, String comment) {
        try {
            User owner = ts.getUser();  // lazy — safe inside @Transactional

            DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("MMM d, yyyy");
            DateTimeFormatter dtFmt   = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a");

            String periodRange = buildPeriodRange(ts.getPeriodStart(), ts.getPeriodEnd(), dateFmt);
            String reviewedAt  = ts.getReviewedAt() != null
                    ? ts.getReviewedAt().format(dtFmt) : "—";
            String title = (ts.getTitle() != null && !ts.getTitle().isBlank())
                    ? escapeHtml(ts.getTitle())
                    : "Timesheet #" + ts.getId();

            BigDecimal total = ts.getEntries().stream()
                    .map(TimesheetEntry::getHoursWorked)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            String entryRowsHtml = buildEntryRowsHtml(ts.getEntries(), dateFmt);

            log.info("[TS_STATUS_EMAIL] Triggering '{}' notification for userId={}, email='{}'",
                    ts.getStatus().name(), owner.getId(), owner.getEmail());

            emailService.sendTimesheetStatusEmail(
                    owner.getEmail(),
                    owner.getFirstName(),
                    title,
                    ts.getStatus().name(),
                    periodRange,
                    reviewer.getFirstName() + " " + reviewer.getLastName(),
                    comment != null ? escapeHtml(comment) : null,
                    reviewedAt,
                    total.toPlainString(),
                    entryRowsHtml
            );

        } catch (Exception ex) {
            // Notification failure must not affect the review result
            log.warn("[TS_STATUS_EMAIL] Failed to trigger notification for timesheet id={}: {}",
                    ts.getId(), ex.getMessage(), ex);
        }
    }

    /**
     * Builds alternating-row HTML {@code <tr>} elements for every entry in the
     * timesheet, sorted by work date ascending.
     */
    private String buildEntryRowsHtml(List<TimesheetEntry> entries, DateTimeFormatter dateFmt) {
        if (entries == null || entries.isEmpty()) {
            return "<tr><td colspan=\"4\" style=\"padding:18px;text-align:center;"
                    + "color:#64748b;font-style:italic;font-size:13px;\">No entries recorded.</td></tr>";
        }

        List<TimesheetEntry> sorted = entries.stream()
                .sorted(Comparator.comparing(TimesheetEntry::getWorkDate))
                .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sorted.size(); i++) {
            TimesheetEntry e   = sorted.get(i);
            String rowBg       = (i % 2 == 0) ? "#ffffff" : "#f8fafc";
            String date        = e.getWorkDate() != null ? e.getWorkDate().format(dateFmt) : "—";
            String projectName = escapeHtml(e.getProject().getName());
            String projectCode = e.getProject().getCode() != null
                    ? " <span style=\"color:#94a3b8;font-size:11px;\">(" + escapeHtml(e.getProject().getCode()) + ")</span>"
                    : "";
            String notes = (e.getDescription() != null && !e.getDescription().isBlank())
                    ? escapeHtml(e.getDescription())
                    : "<em style=\"color:#94a3b8;\">&#8212;</em>";

            sb.append("<tr style=\"background:").append(rowBg).append(";border-bottom:1px solid #e2e8f0;\">")
              .append("<td style=\"padding:11px 15px;color:#0f172a;font-size:13px;font-weight:500;\">")
                  .append(projectName).append(projectCode).append("</td>")
              .append("<td style=\"padding:11px 15px;color:#475569;font-size:13px;white-space:nowrap;\">")
                  .append(date).append("</td>")
              .append("<td style=\"padding:11px 15px;color:#0f172a;font-size:13px;font-weight:700;text-align:right;white-space:nowrap;\">")
                  .append(e.getHoursWorked()).append(" h</td>")
              .append("<td style=\"padding:11px 15px;color:#64748b;font-size:12px;\">")
                  .append(notes).append("</td>")
              .append("</tr>");
        }
        return sb.toString();
    }

    /** Formats a LocalDate period into a human-readable range string. */
    private String buildPeriodRange(LocalDate start, LocalDate end, DateTimeFormatter fmt) {
        if (start == null && end == null) return "—";
        if (start == null)                return "Up to " + end.format(fmt);
        if (end   == null)                return "From " + start.format(fmt);
        return start.format(fmt) + " \u2013 " + end.format(fmt);  // en-dash
    }

    /** Minimal HTML escaping for user-supplied text inserted into the email body. */
    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&",  "&amp;")
                   .replace("<",  "&lt;")
                   .replace(">",  "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'",  "&#39;");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Private finders / assertions
    // ══════════════════════════════════════════════════════════════════════
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

    /**
     * Verifies that the given manager is authorised to review (approve/reject)
     * the given timesheet.
     *
     * <p>Business rules:
     * <ul>
     *   <li><b>ROLE_ADMIN</b> — full access, scope check is bypassed.</li>
     *   <li><b>ROLE_MANAGER</b> — may only review timesheets whose owner's
     *       {@code manager_id} equals this manager's {@code id}.  If the
     *       employee has no manager assigned, or a different manager, a
     *       {@link ForbiddenOperationException} is thrown.</li>
     * </ul>
     *
     * <p>Called inside an open {@code @Transactional} context so the lazy
     * {@code user.manager} association is safely reachable.
     *
     * @param ts      the timesheet being reviewed (owner already loaded)
     * @param manager the authenticated manager requesting the action
     * @throws ForbiddenOperationException if the manager does not own the employee
     */
    private void assertManagerCanReview(Timesheet ts, User manager) {

        // ── Admin bypass ──────────────────────────────────────────────────
        boolean isAdmin = manager.getRoles().stream()
                .anyMatch(r -> r.getName() == RoleName.ROLE_ADMIN);

        if (isAdmin) {
            log.debug("[TS_SCOPE] Admin '{}' — scope check bypassed for timesheet id={}",
                    manager.getUsername(), ts.getId());
            return;
        }

        // ── Direct-report check ───────────────────────────────────────────
        User   owner        = ts.getUser();
        User   ownerManager = owner.getManager();   // lazy — safe inside @Transactional

        if (ownerManager == null) {
            log.warn("[TS_SCOPE] Timesheet id={} owner='{}' has no manager assigned. " +
                     "Manager '{}' (id={}) denied.",
                    ts.getId(), owner.getUsername(), manager.getUsername(), manager.getId());
            throw new ForbiddenOperationException(
                    "You are not authorised to review this timesheet. " +
                    "The employee currently has no manager assigned.");
        }

        if (!ownerManager.getId().equals(manager.getId())) {
            log.warn("[TS_SCOPE] Manager '{}' (id={}) attempted to review timesheet id={} " +
                     "belonging to user '{}' (id={}) who reports to manager id={}, not to them.",
                    manager.getUsername(), manager.getId(),
                    ts.getId(),
                    owner.getUsername(), owner.getId(),
                    ownerManager.getId());
            throw new ForbiddenOperationException(
                    "You are not authorised to review this timesheet. " +
                    "You can only approve or reject timesheets of employees who report directly to you.");
        }

        log.debug("[TS_SCOPE] Manager '{}' (id={}) authorised to review timesheet id={} of user '{}'",
                manager.getUsername(), manager.getId(), ts.getId(), owner.getUsername());
    }
}
