package com.timesheetManagement.entity;

/**
 * Lifecycle states of a {@link Timesheet}.
 *
 * <pre>
 *   DRAFT  ──submit──►  SUBMITTED  ──approve──►  APPROVED
 *                            └────reject──►  REJECTED
 * </pre>
 *
 * Only a DRAFT timesheet can be edited or deleted.
 * Only a SUBMITTED timesheet can be reviewed by an admin.
 */
public enum TimesheetStatus {
    /** Employee is still filling in entries — editable, deletable. */
    DRAFT,

    /** Sent for manager review — no further edits allowed. */
    SUBMITTED,

    /** Approved by an admin/manager — final state. */
    APPROVED,

    /** Rejected by an admin/manager — employee must create a new timesheet. */
    REJECTED
}

