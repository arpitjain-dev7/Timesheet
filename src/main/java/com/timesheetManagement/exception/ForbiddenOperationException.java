package com.timesheetManagement.exception;

/**
 * Thrown when a business-level permission rule is violated, separate from
 * Spring Security's authentication/authorization checks. Examples:
 * <ul>
 *   <li>Manager trying to approve their own timesheet</li>
 *   <li>User trying to log time against an unassigned project</li>
 * </ul>
 *
 * Maps to HTTP 403 Forbidden.
 */
public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException(String message) {
        super(message);
    }
}

