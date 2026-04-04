package com.timesheetManagement.exception;

/**
 * Thrown when a state-transition is attempted on a {@code Timesheet}
 * that is not in the required state.
 *
 * <p>Examples:
 * <ul>
 *   <li>Submitting a timesheet that is already SUBMITTED / APPROVED / REJECTED</li>
 *   <li>Adding/removing entries from a non-DRAFT timesheet</li>
 *   <li>Approving a timesheet that was not SUBMITTED</li>
 * </ul>
 *
 * Maps to HTTP 409 Conflict via {@link GlobalExceptionHandler}.
 */
public class InvalidTimesheetStateException extends RuntimeException {

    public InvalidTimesheetStateException(String message) {
        super(message);
    }
}

