package com.timesheetManagement.exception;

/**
 * Thrown when a user attempts to create a {@code Timesheet} for a week
 * they already have one for (unique constraint: user + weekStartDate).
 *
 * Maps to HTTP 409 Conflict via {@link GlobalExceptionHandler}.
 */
public class DuplicateTimesheetException extends RuntimeException {

    public DuplicateTimesheetException(String message) {
        super(message);
    }
}

