package com.timesheetManagement.exception;

/**
 * Thrown when a user attempts to create a duplicate timesheet entry
 * (same project + date already exists in this timesheet) or a duplicate
 * project assignment (user already assigned to that project).
 *
 * Maps to HTTP 409 Conflict.
 */
public class DuplicateEntryException extends RuntimeException {

    public DuplicateEntryException(String message) {
        super(message);
    }
}

