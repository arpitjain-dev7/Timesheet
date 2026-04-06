package com.timesheetManagement.exception;

/**
 * Thrown when a manager-assignment rule is violated, for example:
 * <ul>
 *   <li>A USER is created without a {@code managerId}.</li>
 *   <li>A MANAGER or ADMIN is created with a non-null {@code managerId}.</li>
 *   <li>The referenced manager does not hold the {@code ROLE_MANAGER} role.</li>
 *   <li>A user attempts to assign themselves as their own manager.</li>
 * </ul>
 *
 * <p>Mapped to HTTP 409 Conflict by {@link GlobalExceptionHandler}.
 */
public class InvalidManagerAssignmentException extends RuntimeException {

    public InvalidManagerAssignmentException(String message) {
        super(message);
    }
}

